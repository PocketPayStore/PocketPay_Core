package pocketpaystore.pocketpay_core.payment.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
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

	private final OrderRepository orderRepository;
	private final PaymentRefundStateService refundStateService;
	private final PgClient pgClient;
	private final IdempotencyKeyGuard idempotencyKeyGuard;

	public RefundResponse refund(Long memberId, String orderNumber, CreateRefundRequest request,
								  String idempotencyKey) {
		if (!idempotencyKeyGuard.tryAcquire(IDEMPOTENCY_NAMESPACE, idempotencyKey)) {
			throw new CustomException(CommonErrorCode.DUPLICATE_REQUEST);
		}

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
			pgClient.cancel(new CancelRequest(payment.getPgTransactionId(), cancelAmount, CANCEL_REASON, idempotencyKey));
		} catch (Exception e) {
			log.error("[Refund] PG 취소 호출 실패 (best-effort, 로컬 상태는 그대로 반영): paymentId={}", payment.getId(), e);
		}
	}

}
