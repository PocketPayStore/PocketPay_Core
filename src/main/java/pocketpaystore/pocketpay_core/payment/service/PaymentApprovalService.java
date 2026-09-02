package pocketpaystore.pocketpay_core.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import feign.FeignException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import pocketpaystore.pocketpay_core.common.alert.CriticalAlertService;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PointErrorCode;
import pocketpaystore.pocketpay_core.common.idempotency.IdempotencyKeyGuard;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.dto.request.ApprovePaymentRequest;
import pocketpaystore.pocketpay_core.payment.dto.response.PaymentResponse;
import pocketpaystore.pocketpay_core.pg.client.PgClient;
import pocketpaystore.pocketpay_core.pg.dto.request.ApprovalRequest;
import pocketpaystore.pocketpay_core.point.repository.PointBalanceRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApprovalService {

	private static final String IDEMPOTENCY_NAMESPACE = "payment";

	private final OrderRepository orderRepository;
	private final PaymentStateService paymentStateService;
	private final PgClient pgClient;
	private final IdempotencyKeyGuard idempotencyKeyGuard;
	private final PaymentCompletionService paymentCompletionService;
	private final CriticalAlertService criticalAlertService;
	private final PointBalanceRepository pointBalanceRepository;
	private final ObjectMapper objectMapper;

	@Value("${pg.provider-name:mock-pg}")
	private String pgProviderName;

	public PaymentResponse approve(Long memberId, String orderNumber, String idempotencyKey,
									ApprovePaymentRequest request) {
		PaymentResponse cached = readCachedResult(idempotencyKey);
		if (cached != null) {
			return requireSameOrder(cached, orderNumber);
		}

		if (!idempotencyKeyGuard.tryAcquire(IDEMPOTENCY_NAMESPACE, idempotencyKey)) {
			String json = idempotencyKeyGuard.waitForCachedResult(IDEMPOTENCY_NAMESPACE, idempotencyKey);
			PaymentResponse paymentResponse = deserializeOrThrow(idempotencyKey, json);
			return requireSameOrder(paymentResponse, orderNumber);
		}

		try {
			PaymentResponse response = doApprove(memberId, orderNumber, idempotencyKey,
					request.getPaymentKey(), request.getUsePointAmount(), request.getAmount());
			if (PaymentStatus.DONE.name().equals(response.getStatus())) {
				cacheResult(idempotencyKey, response);
			}
			return response;
		} finally {
			idempotencyKeyGuard.release(IDEMPOTENCY_NAMESPACE, idempotencyKey);
		}
	}

	private PaymentResponse requireSameOrder(PaymentResponse response, String orderNumber) {
		if (!orderNumber.equals(response.getOrderNumber())) {
			throw new CustomException(CommonErrorCode.IDEMPOTENCY_KEY_MISMATCH);
		}
		return response;
	}

	private PaymentResponse readCachedResult(String idempotencyKey) {
		String json = idempotencyKeyGuard.getCachedResult(IDEMPOTENCY_NAMESPACE, idempotencyKey);
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json, PaymentResponse.class);
		} catch (Exception e) {
			log.error("[Payment] 캐시된 응답 역직렬화 실패, 캐시 무시: idempotencyKey={}", idempotencyKey, e);
			return null;
		}
	}

	private PaymentResponse deserializeOrThrow(String idempotencyKey, String json) {
		try {
			return objectMapper.readValue(json, PaymentResponse.class);
		} catch (Exception e) {
			log.error("[Payment] 대기 후 받은 캐시 응답 역직렬화 실패: idempotencyKey={}", idempotencyKey, e);
			throw new CustomException(CommonErrorCode.REQUEST_TIMEOUT);
		}
	}

	private void cacheResult(String idempotencyKey, PaymentResponse response) {
		try {
			idempotencyKeyGuard.cacheResult(IDEMPOTENCY_NAMESPACE, idempotencyKey, objectMapper.writeValueAsString(response));
		} catch (Exception e) {
			log.error("[Payment] 응답 캐싱 실패: idempotencyKey={}", idempotencyKey, e);
		}
	}

	private PaymentResponse doApprove(Long memberId, String orderNumber, String idempotencyKey,
									   String paymentKey, long usePointAmount, long authorizedAmount) {
		Order order = orderRepository.findByOrderNumber(orderNumber)
				.orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
		if (!order.getMemberId().equals(memberId)) {
			throw new CustomException(OrderErrorCode.ORDER_NOT_FOUND);
		}
		validateUsePointAmount(memberId, usePointAmount, order.getTotalAmount());
		long pgAmount = order.getTotalAmount() - usePointAmount;
		if (pgAmount != authorizedAmount) {
			log.info("[Payment] 승인 요청 금액 불일치, PG 호출 없이 즉시 거절: orderId={}, expected={}, authorized={}",
					order.getId(), pgAmount, authorizedAmount);
			throw new CustomException(PaymentErrorCode.AUTHORIZED_AMOUNT_MISMATCH);
		}

		Long paymentId;
		try {
			paymentId = paymentStateService.initiate(
					order.getId(), idempotencyKey, pgAmount, usePointAmount, paymentKey, pgProviderName);
		} catch (DataIntegrityViolationException e) {
			throw new CustomException(CommonErrorCode.DUPLICATE_REQUEST);
		}

		if (pgAmount == 0) {
			Payment payment = completePayment(paymentId, order, null, pgAmount);
			return PaymentResponse.from(payment, orderNumber);
		}

		try {
			var approval = pgClient.approve(idempotencyKey, new ApprovalRequest(paymentKey, pgAmount, order.getOrderNumber()));
			Payment payment = completePayment(paymentId, order, paymentKey, pgAmount);
			return PaymentResponse.from(payment, orderNumber);
		} catch (FeignException e) {
			if (isUserFault(e)) {
				log.info("[Payment] PG 승인 거절(유저 귀책, 재시도 없이 즉시 실패): orderId={}, status={}", order.getId(), e.status());
				Payment payment = paymentStateService.markPaymentFailed(
						paymentId, String.valueOf(e.status()), e.contentUTF8());
				return PaymentResponse.from(payment, orderNumber);
			}
			log.error("[Payment] PG 승인 재시도 소진(시스템 장애): orderId={}", order.getId(), e);
			Payment payment = paymentStateService.markTimeoutUnknown(paymentId);
			return PaymentResponse.from(payment, orderNumber);
		} catch (Exception e) {
			log.error("[Payment] PG 승인 호출 실패(네트워크, 재시도 소진): orderId={}", order.getId(), e);
			Payment payment = paymentStateService.markTimeoutUnknown(paymentId);
			return PaymentResponse.from(payment, orderNumber);
		}
	}

	private Payment completePayment(Long paymentId, Order order, String paymentKey, long pgAmount) {
		Payment payment;
		try {
			payment = paymentStateService.markDone(paymentId, order.getId());
		} catch (Exception e) {
			criticalAlertService.alertPgApprovedButPersistFailed(order.getId(), paymentId, paymentKey, pgAmount, e);
			throw e;
		}
		paymentCompletionService.complete(paymentId, order.getId());
		return payment;
	}

	private void validateUsePointAmount(Long memberId, long usePointAmount, Long orderTotalAmount) {
		if (usePointAmount < 0 || usePointAmount > orderTotalAmount) {
			throw new CustomException(PaymentErrorCode.INVALID_POINT_USE_AMOUNT);
		}
		if (usePointAmount == 0) {
			return;
		}
		long balance = pointBalanceRepository.findBalanceByMemberId(memberId).orElse(0L);
		if (balance < usePointAmount) {
			throw new CustomException(PointErrorCode.INSUFFICIENT_POINT_BALANCE);
		}
	}

	private boolean isUserFault(FeignException e) {
		return e.status() >= 400 && e.status() < 500;
	}

}
