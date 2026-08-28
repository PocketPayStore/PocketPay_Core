package pocketpaystore.pocketpay_core.payment.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.CommonErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;
import pocketpaystore.pocketpay_core.order.repository.OrderItemRepository;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentCancel;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatusHistory;
import pocketpaystore.pocketpay_core.payment.domain.Refund;
import pocketpaystore.pocketpay_core.payment.dto.response.PreparedRefund;
import pocketpaystore.pocketpay_core.payment.event.publisher.PaymentStatusEventPublisher;
import pocketpaystore.pocketpay_core.payment.repository.PaymentCancelRepository;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;
import pocketpaystore.pocketpay_core.payment.repository.PaymentStatusHistoryRepository;
import pocketpaystore.pocketpay_core.payment.repository.RefundRepository;

@Service
@RequiredArgsConstructor
public class PaymentRefundStateService {

	private final PaymentRepository paymentRepository;
	private final OrderItemRepository orderItemRepository;
	private final RefundRepository refundRepository;
	private final PaymentCancelRepository paymentCancelRepository;
	private final PaymentStatusHistoryRepository paymentStatusHistoryRepository;
	private final OrderRepository orderRepository;
	private final PaymentStatusEventPublisher statusEventPublisher;

	@Transactional
	public PreparedRefund prepare(Long orderId, int quantity, String idempotencyKey) {
		Payment payment = paymentRepository.findRefundableByOrderIdWithLock(orderId)
				.orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

		OrderItem orderItem = orderItemRepository.findByOrderId(orderId)
				.orElseThrow(() -> new CustomException(OrderErrorCode.EMPTY_ORDER_ITEMS));
		if (quantity > orderItem.getQuantity()) {
			throw new CustomException(PaymentErrorCode.EXCESSIVE_REFUND_AMOUNT);
		}
		long refundAmount = orderItem.getUnitPrice() * quantity;

		Refund refund;
		try {
			refund = refundRepository.save(Refund.create(payment.getId(), refundAmount, idempotencyKey));
		} catch (DataIntegrityViolationException e) {
			throw new CustomException(CommonErrorCode.DUPLICATE_REQUEST);
		}

		try {
			payment.refund(refundAmount);
		} catch (CustomException e) {
			refund.toRejected();
			throw e;
		}

		refund.toProcessing();
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(payment.getId(), payment.getStatus()));
		Order order = orderRepository.findById(payment.getOrderId())
				.orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
		statusEventPublisher.publish(payment, order);
		return PreparedRefund.builder().refund(refund).payment(payment).build();
	}

	@Transactional
	public Refund complete(Long paymentId, Long refundId, Long refundAmount, String reason) {
		PaymentCancel paymentCancel = PaymentCancel.create(paymentId, refundId, refundAmount, reason);
		paymentCancelRepository.save(paymentCancel);
		Refund refund = refundRepository.findById(refundId)
				.orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
		refund.toCompleted();
		return refund;
	}

}
