package pocketpaystore.pocketpay_core.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.OrderExpiredException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;
import pocketpaystore.pocketpay_core.order.repository.OrderItemRepository;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentMethod;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatusHistory;
import pocketpaystore.pocketpay_core.payment.event.model.PaymentCompletedEvent;
import pocketpaystore.pocketpay_core.payment.event.publisher.PaymentStatusEventPublisher;
import pocketpaystore.pocketpay_core.payment.repository.PaymentStatusHistoryRepository;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;
import pocketpaystore.pocketpay_core.point.service.PointEarnLogService;
import pocketpaystore.pocketpay_core.point.service.PointReservationService;
import pocketpaystore.pocketpay_core.product.service.StockRestorationService;
import pocketpaystore.pocketpay_core.product.service.StockService;

@Service
@RequiredArgsConstructor
public class PaymentStateService {

	private final PaymentRepository paymentRepository;
	private final PaymentStatusHistoryRepository paymentStatusHistoryRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final PaymentStatusEventPublisher statusEventPublisher;
	private final PointReservationService pointReservationService;
	private final StockRestorationService stockRestorationService;
	private final PointEarnLogService pointEarnLogService;
	private final StockService stockService;
	private final ApplicationEventPublisher eventPublisher;

	@Value("${payment.point-earn-rate}")
	private double pointEarnRate;

	@Transactional(noRollbackFor = OrderExpiredException.class)
	public Long initiate(Long orderId, String idempotencyKey, Long amount, Long usedPointAmount,
						  String paymentKey, String pgProviderName) {
		Order order = findOrderForUpdate(orderId);
		if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.IN_PROGRESS)) {
			throw new CustomException(PaymentErrorCode.PAYMENT_ALREADY_IN_PROGRESS);
		}
		if (order.isReservationExpired()) {
			expireReservation(order);
			throw new OrderExpiredException();
		}
		order.markPaymentPending();

		Payment payment = Payment.create(
				orderId, PaymentMethod.CARD, pgProviderName, idempotencyKey, amount, usedPointAmount, paymentKey);
		Payment saved = paymentRepository.save(payment);
		pointReservationService.reserve(saved.getId(), order.getMemberId(), usedPointAmount);
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(saved.getId(), saved.getStatus()));
		statusEventPublisher.publish(saved, order);

		saved.toInProgress();
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(saved.getId(), saved.getStatus()));
		statusEventPublisher.publish(saved, order);
		return saved.getId();
	}

	@Transactional
	public Payment markDone(Long paymentId, Long orderId) {
		Payment payment = findPayment(paymentId);
		if (payment.getUsedPointAmount() > 0) {
			pointReservationService.confirm(paymentId, orderId);
		}
		payment.toDone();
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(payment.getId(), payment.getStatus()));
		Order order = findOrder(orderId);
		order.markPaid();

		long earnAmount = Math.round(payment.getAmount() * pointEarnRate);
		pointEarnLogService.requestEarn(order.getMemberId(), orderId, paymentId, earnAmount);
		stockService.confirmForOrder(orderId);

		statusEventPublisher.publish(payment, order);
		eventPublisher.publishEvent(new PaymentCompletedEvent(paymentId));
		return payment;
	}

	@Transactional
	public Payment markPaymentFailed(Long paymentId, String failureCode, String failureMessage) {
		Payment payment = findPayment(paymentId);
		if (payment.getUsedPointAmount() > 0) {
			pointReservationService.release(paymentId);
		}
		payment.toFailed(failureCode, failureMessage);
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(payment.getId(), payment.getStatus()));
		Order order = findOrder(payment.getOrderId());
		statusEventPublisher.publish(payment, order);
		return payment;
	}

	@Transactional
	public Payment markTimeoutUnknown(Long paymentId) {
		Payment payment = findPayment(paymentId);
		payment.toTimeoutUnknown();
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(payment.getId(), payment.getStatus()));
		Order order = findOrder(payment.getOrderId());
		statusEventPublisher.publish(payment, order);
		return payment;
	}

	private void expireReservation(Order order) {
		OrderItem item = orderItemRepository.findByOrderId(order.getId())
				.orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
		stockRestorationService.restore(item.getProductId(), item.getQuantity());
		order.expire();
	}

	private Payment findPayment(Long paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
	}

	private Order findOrder(Long orderId) {
		return orderRepository.findById(orderId)
				.orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
	}

	private Order findOrderForUpdate(Long orderId) {
		return orderRepository.findByIdWithLock(orderId)
				.orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
	}

}
