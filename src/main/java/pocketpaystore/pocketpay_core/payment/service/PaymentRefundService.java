package pocketpaystore.pocketpay_core.payment.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
import pocketpaystore.pocketpay_core.common.idempotency.IdempotencyKeyGuard;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.Refund;
import pocketpaystore.pocketpay_core.payment.dto.request.CreateRefundRequest;
import pocketpaystore.pocketpay_core.payment.dto.response.PreparedRefund;
import pocketpaystore.pocketpay_core.payment.dto.response.RefundResponse;
import pocketpaystore.pocketpay_core.pg.client.PgClient;
import pocketpaystore.pocketpay_core.pg.dto.request.CancelRequest;
import pocketpaystore.pocketpay_core.pg.dto.response.ApprovalResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

	private static final String IDEMPOTENCY_NAMESPACE = "refund";
	private static final String CANCEL_REASON = "REFUND";

	private final OrderRepository orderRepository;
	private final PaymentRefundStateService refundStateService;
	private final PgClient pgClient;
	private final IdempotencyKeyGuard idempotencyKeyGuard;
	private final ObjectMapper objectMapper;

	public RefundResponse refund(Long memberId, String orderNumber, CreateRefundRequest request, String idempotencyKey) {
		String namespace = buildNamespace(memberId, orderNumber);

		RefundResponse cached = readCachedResult(namespace, idempotencyKey);
		if (cached != null) {
			return cached;
		}

		if (!idempotencyKeyGuard.tryAcquire(namespace, idempotencyKey)) {
			String json = idempotencyKeyGuard.waitForCachedResult(
					namespace, idempotencyKey, PaymentErrorCode.REFUND_TIMEOUT);
			return deserializeOrThrow(idempotencyKey, json);
		}

		try {
			RefundResponse response = doRefund(memberId, orderNumber, idempotencyKey, request);
			cacheResult(namespace, idempotencyKey, response);
			return response;
		} finally {
			idempotencyKeyGuard.release(namespace, idempotencyKey);
		}
	}

	private RefundResponse doRefund(Long memberId, String orderNumber, String idempotencyKey, CreateRefundRequest request) {
		Order order = orderRepository.findByOrderNumber(orderNumber)
				.orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
		if (!order.getMemberId().equals(memberId)) {
			throw new CustomException(OrderErrorCode.ORDER_NOT_FOUND);
		}

		PreparedRefund prepared = refundStateService.prepare(order.getId(), request.getQuantity(), idempotencyKey);
		Payment payment = prepared.getPayment();
		Refund refund = prepared.getRefund();

		boolean pgCancelConfirmed = tryCancelPg(payment, refund.getRequestAmount(), idempotencyKey);

		Refund completedRefund = refundStateService.complete(
				payment.getId(), refund.getId(), refund.getRequestAmount(), request.getReason(), pgCancelConfirmed);

		return RefundResponse.of(completedRefund, payment);
	}

	private boolean tryCancelPg(Payment payment, Long cancelAmount, String idempotencyKey) {
		try {
			pgClient.cancel(payment.getPgTransactionId(), idempotencyKey, new CancelRequest(CANCEL_REASON, cancelAmount));
			return true;
		} catch (Exception e) {
			log.error("[Refund] PG 취소 호출 실패, 즉시 재조회 시도: paymentId={}", payment.getId(), e);
			return resolveCancelByImmediateInquiry(payment);
		}
	}

	private boolean resolveCancelByImmediateInquiry(Payment payment) {
		try {
			ApprovalResponse inquiry = pgClient.inquire(payment.getPgTransactionId());
			if (isCanceledStatus(inquiry.getStatus())) {
				log.info("[Refund] 즉시 재조회로 PG 취소 확인: paymentId={}", payment.getId());
				return true;
			}
			log.error("[Refund] 즉시 재조회로도 PG 취소 미확인({}), 배치가 재시도: paymentId={}", inquiry.getStatus(), payment.getId());
			return false;
		} catch (Exception e) {
			log.error("[Refund] 즉시 재조회 실패, 배치가 재시도: paymentId={}", payment.getId(), e);
			return false;
		}
	}

	private boolean isCanceledStatus(String status) {
		return "CANCELED".equals(status) || "PARTIAL_CANCELED".equals(status);
	}

	private RefundResponse readCachedResult(String namespace, String idempotencyKey) {
		String json = idempotencyKeyGuard.getCachedResult(namespace, idempotencyKey);
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json, RefundResponse.class);
		} catch (Exception e) {
			log.error("[Refund] 캐시된 응답 역직렬화 실패, 캐시 무시: idempotencyKey={}", idempotencyKey, e);
			return null;
		}
	}

	private RefundResponse deserializeOrThrow(String idempotencyKey, String json) {
		try {
			return objectMapper.readValue(json, RefundResponse.class);
		} catch (Exception e) {
			log.error("[Refund] 대기 후 받은 캐시 응답 역직렬화 실패: idempotencyKey={}", idempotencyKey, e);
			throw new CustomException(PaymentErrorCode.REFUND_RESULT_UNREADABLE);
		}
	}

	private void cacheResult(String namespace, String idempotencyKey, RefundResponse response) {
		try {
			idempotencyKeyGuard.cacheResult(namespace, idempotencyKey, objectMapper.writeValueAsString(response));
		} catch (Exception e) {
			log.error("[Refund] 응답 캐싱 실패: idempotencyKey={}", idempotencyKey, e);
		}
	}

	private String buildNamespace(Long memberId, String orderNumber) {
		return IDEMPOTENCY_NAMESPACE + ":" + memberId + ":" + orderNumber;
	}

}
