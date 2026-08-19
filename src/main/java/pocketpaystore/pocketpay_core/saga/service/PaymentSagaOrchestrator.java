package pocketpaystore.pocketpay_core.saga.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;
import pocketpaystore.pocketpay_core.notification.service.NotificationService;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.repository.OrderRepository;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.repository.PaymentRepository;
import pocketpaystore.pocketpay_core.point.service.PointService;
import pocketpaystore.pocketpay_core.product.service.StockService;
import pocketpaystore.pocketpay_core.saga.domain.SagaStep;
import pocketpaystore.pocketpay_core.settlement.service.SettlementService;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSagaOrchestrator {

	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;
	private final PointService pointService;
	private final StockService stockService;
	private final NotificationService notificationService;
	private final SettlementService settlementService;
	private final SagaLogService sagaLogService;
	private final PaymentCompensationService compensationService;

	@Value("${payment.point-earn-rate}")
	private double pointEarnRate;

	public void runSagaAfterApproval(Long paymentId, Long orderId) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

		long earnAmount = Math.round(payment.getAmount() * pointEarnRate);
		long usedPointAmount = payment.getUsedPointAmount();
		boolean pointUsed = false;
		boolean pointEarned = false;

		try {
			if (usedPointAmount > 0) {
				usePoints(orderId, order.getMemberId(), usedPointAmount);
				pointUsed = true;
			}

			earnPoints(orderId, order.getMemberId(), earnAmount);
			pointEarned = true;

			confirmStock(orderId);
		} catch (Exception e) {
			log.error("[Saga] 결제 사가 실패, 보상 시작: orderId={}", orderId, e);
			compensationService.compensate(
					paymentId, orderId, order.getMemberId(), pointUsed, usedPointAmount, pointEarned, earnAmount);
			return;
		}

		sendNotification(orderId, paymentId);
		createSettlement(orderId, paymentId);
	}

	private void usePoints(Long orderId, Long memberId, long amount) {
		Long sagaLogId = sagaLogService.start(orderId, SagaStep.POINT_USE);
		try {
			pointService.use(memberId, orderId, amount);
			sagaLogService.success(sagaLogId);
		} catch (Exception e) {
			sagaLogService.fail(sagaLogId, e.getMessage());
			throw e;
		}
	}

	private void earnPoints(Long orderId, Long memberId, long amount) {
		Long sagaLogId = sagaLogService.start(orderId, SagaStep.POINT_EARN);
		try {
			pointService.earn(memberId, orderId, amount);
			sagaLogService.success(sagaLogId);
		} catch (Exception e) {
			sagaLogService.fail(sagaLogId, e.getMessage());
			throw e;
		}
	}

	private void confirmStock(Long orderId) {
		Long sagaLogId = sagaLogService.start(orderId, SagaStep.STOCK_CONFIRM);
		try {
			stockService.confirmForOrder(orderId);
			sagaLogService.success(sagaLogId);
		} catch (Exception e) {
			sagaLogService.fail(sagaLogId, e.getMessage());
			throw e;
		}
	}

	private void sendNotification(Long orderId, Long paymentId) {
		Long sagaLogId = sagaLogService.start(orderId, SagaStep.NOTIFICATION);
		try {
			notificationService.notify(paymentId);
			sagaLogService.success(sagaLogId);
		} catch (Exception e) {
			log.error("[Saga] {} 스텝 실패(보상 대상 아님): orderId={}", SagaStep.NOTIFICATION, orderId, e);
			sagaLogService.fail(sagaLogId, e.getMessage());
		}
	}

	private void createSettlement(Long orderId, Long paymentId) {
		Long sagaLogId = sagaLogService.start(orderId, SagaStep.SETTLEMENT);
		try {
			settlementService.create(paymentId);
			sagaLogService.success(sagaLogId);
		} catch (Exception e) {
			log.error("[Saga] {} 스텝 실패(보상 대상 아님): orderId={}", SagaStep.SETTLEMENT, orderId, e);
			sagaLogService.fail(sagaLogId, e.getMessage());
		}
	}

}
