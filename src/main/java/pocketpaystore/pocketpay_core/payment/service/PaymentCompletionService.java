package pocketpaystore.pocketpay_core.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pocketpaystore.pocketpay_core.common.alert.CriticalAlertService;
import pocketpaystore.pocketpay_core.notification.service.NotificationService;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;
import pocketpaystore.pocketpay_core.payment.domain.PaymentCompletionStep;
import pocketpaystore.pocketpay_core.point.service.PointService;
import pocketpaystore.pocketpay_core.product.service.StockService;
import pocketpaystore.pocketpay_core.settlement.service.SettlementService;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompletionService {
	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;
	private final PointService pointService;
	private final StockService stockService;
	private final NotificationService notificationService;
	private final SettlementService settlementService;
	private final CriticalAlertService criticalAlertService;

	@Value("${payment.point-earn-rate}")
	private double pointEarnRate;

	public void complete(Long paymentId, Long orderId) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
		try {
			pointService.earn(order.getMemberId(), orderId, Math.round(payment.getAmount() * pointEarnRate));
		} catch (Exception e) {
			criticalAlertService.alertPaymentPostProcessingFailed(PaymentCompletionStep.POINT_EARN, orderId, paymentId, e);
		}
		try {
			stockService.confirmForOrder(orderId);
		} catch (Exception e) {
			criticalAlertService.alertStockConfirmationFailed(orderId, paymentId, e);
		}
		try {
			notificationService.notify(paymentId);
		} catch (Exception e) {
			criticalAlertService.alertPaymentPostProcessingFailed(PaymentCompletionStep.NOTIFICATION, orderId, paymentId, e);
		}
		try {
			settlementService.create(paymentId);
		} catch (Exception e) {
			criticalAlertService.alertPaymentPostProcessingFailed(PaymentCompletionStep.SETTLEMENT, orderId, paymentId, e);
		}
	}
}
