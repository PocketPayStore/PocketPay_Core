package pocketpaystore.pocketpay_core.payment.service;

import java.time.Instant;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

	private static final String IDEMPOTENCY_NAMESPACE = "refund";
	private static final String CANCEL_REASON = "REFUND";
	private static final int RETRY_WINDOW_SECONDS = 5;

	private final OrderRepository orderRepository;
	private final PaymentRefundStateService refundStateService;
	private final PgClient pgClient;
	private final IdempotencyKeyGuard idempotencyKeyGuard;
	private final ObjectMapper objectMapper;

	public RefundResponse refund(Long memberId, String orderNumber, CreateRefundRequest request) {
		String idempotencyKey = generateIdempotencyKey(orderNumber, request.getQuantity(), request.getReason());

		RefundResponse cached = readCachedResult(idempotencyKey);
		if (cached != null) {
			return cached;
		}

		if (!idempotencyKeyGuard.tryAcquire(IDEMPOTENCY_NAMESPACE, idempotencyKey)) {
			String json = idempotencyKeyGuard.waitForCachedResult(
					IDEMPOTENCY_NAMESPACE, idempotencyKey, PaymentErrorCode.REFUND_TIMEOUT);
			return deserializeOrThrow(idempotencyKey, json);
		}

		try {
			RefundResponse response = doRefund(memberId, orderNumber, idempotencyKey, request);
			cacheResult(idempotencyKey, response);
			return response;
		} finally {
			idempotencyKeyGuard.release(IDEMPOTENCY_NAMESPACE, idempotencyKey);
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

		tryCancelPg(payment, refund.getRequestAmount(), idempotencyKey);

		Refund completedRefund = refundStateService.complete(
				payment.getId(), refund.getId(), refund.getRequestAmount(), request.getReason());

		return RefundResponse.of(completedRefund, payment);
	}

	private void tryCancelPg(Payment payment, Long cancelAmount, String idempotencyKey) {
		try {
			pgClient.cancel(payment.getPgTransactionId(), idempotencyKey, new CancelRequest(CANCEL_REASON, cancelAmount));
		} catch (Exception e) {
			log.error("[Refund] PG 취소 호출 실패 (best-effort, 로컬 상태는 그대로 반영): paymentId={}", payment.getId(), e);
		}
	}

	private RefundResponse readCachedResult(String idempotencyKey) {
		String json = idempotencyKeyGuard.getCachedResult(IDEMPOTENCY_NAMESPACE, idempotencyKey);
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

	private void cacheResult(String idempotencyKey, RefundResponse response) {
		try {
			idempotencyKeyGuard.cacheResult(IDEMPOTENCY_NAMESPACE, idempotencyKey, objectMapper.writeValueAsString(response));
		} catch (Exception e) {
			log.error("[Refund] 응답 캐싱 실패: idempotencyKey={}", idempotencyKey, e);
		}
	}

	private String generateIdempotencyKey(String orderNumber, int quantity, String reason) {
		long retryWindowBucket = Instant.now().getEpochSecond() / RETRY_WINDOW_SECONDS;
		return "refund:" + orderNumber + ":" + quantity + ":" + (reason == null ? "" : reason) + ":" + retryWindowBucket;
	}

}
