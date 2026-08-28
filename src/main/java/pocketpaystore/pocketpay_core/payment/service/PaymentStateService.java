package pocketpaystore.pocketpay_core.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentMethod;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatusHistory;
import pocketpaystore.pocketpay_core.payment.repository.PaymentStatusHistoryRepository;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;

@Service
@RequiredArgsConstructor
public class PaymentStateService {

	private final PaymentRepository paymentRepository;
	private final PaymentStatusHistoryRepository paymentStatusHistoryRepository;
	private final OrderRepository orderRepository;

	@Transactional
	public Long initiate(Long orderId, String idempotencyKey, Long amount, Long usedPointAmount,
						  String paymentKey, String pgProviderName) {
		Order order = findOrderForUpdate(orderId);
		if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.IN_PROGRESS)) {
			throw new CustomException(PaymentErrorCode.PAYMENT_ALREADY_IN_PROGRESS);
		}
		order.markPaymentPending();

		Payment payment = Payment.create(
				orderId, PaymentMethod.CARD, pgProviderName, idempotencyKey, amount, usedPointAmount, paymentKey);
		Payment saved = paymentRepository.save(payment);
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(saved.getId(), saved.getStatus()));

		saved.toInProgress();
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(saved.getId(), saved.getStatus()));
		return saved.getId();
	}

	@Transactional
	public Payment markDone(Long paymentId, Long orderId) {
		Payment payment = findPayment(paymentId);
		payment.toDone();
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(payment.getId(), payment.getStatus()));
		Order order = findOrder(orderId);
		order.markPaid();
		return payment;
	}

	@Transactional
	public Payment markPaymentFailed(Long paymentId, String failureCode, String failureMessage) {
		Payment payment = findPayment(paymentId);
		payment.toFailed(failureCode, failureMessage);
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(payment.getId(), payment.getStatus()));
		return payment;
	}

	@Transactional
	public Payment markTimeoutUnknown(Long paymentId) {
		Payment payment = findPayment(paymentId);
		payment.toTimeoutUnknown();
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(payment.getId(), payment.getStatus()));
		return payment;
	}

	@Transactional
	public void cancelWithOrder(Long paymentId, Long orderId) {
		Payment payment = findPayment(paymentId);
		payment.toCanceled();
		paymentStatusHistoryRepository.save(PaymentStatusHistory.create(payment.getId(), payment.getStatus()));

		Order order = findOrder(orderId);
		order.markCanceled();
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
