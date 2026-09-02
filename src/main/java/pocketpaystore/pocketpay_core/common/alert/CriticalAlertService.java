package pocketpaystore.pocketpay_core.common.alert;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import pocketpaystore.pocketpay_core.payment.domain.AlertSeverity;
import pocketpaystore.pocketpay_core.payment.domain.PaymentAlertType;
import pocketpaystore.pocketpay_core.payment.domain.PaymentCompletionStep;

@Slf4j
@Component
public class CriticalAlertService {
	private final PaymentAlertLogService paymentAlertLogService;

	public CriticalAlertService(PaymentAlertLogService paymentAlertLogService) {
		this.paymentAlertLogService = paymentAlertLogService;
	}

	public void alertPgApprovedButPersistFailed(Long orderId, Long paymentId, String pgTransactionId,
												  Long amount, Throwable cause) {
		log.error("[CriticalAlert] PG 승인 성공, DB 기록 실패 — 수동 대사 필요: orderId={}, paymentId={}, pgTransactionId={}, amount={}",
				orderId, paymentId, pgTransactionId, amount, cause);
		paymentAlertLogService.record(PaymentAlertType.PG_APPROVED_PERSIST_FAILED, AlertSeverity.CRITICAL, paymentId, orderId, "PG 승인 후 DB 기록 실패");
	}

	public void alertStockConfirmationFailed(Long orderId, Long paymentId, Throwable cause) {
		log.error("[Slack][Critical] 결제 승인 후 재고 확정 실패 — 즉시 재처리 필요: orderId={}, paymentId={}",
				orderId, paymentId, cause);
		paymentAlertLogService.record(PaymentAlertType.STOCK_CONFIRMATION_FAILED, AlertSeverity.CRITICAL, paymentId, orderId, "재고 확정 실패");
	}

	public void alertPaymentPostProcessingFailed(PaymentCompletionStep step, Long orderId, Long paymentId, Throwable cause) {
		log.warn("[Slack][Warning] 결제 후처리 실패 — 재처리 필요: step={}, orderId={}, paymentId={}",
				step, orderId, paymentId, cause);
		paymentAlertLogService.record(PaymentAlertType.PAYMENT_COMPLETION_FAILED, AlertSeverity.WARNING, paymentId, orderId, step + " 후처리 실패");
	}

}
