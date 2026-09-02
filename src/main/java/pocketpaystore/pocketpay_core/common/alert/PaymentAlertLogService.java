package pocketpaystore.pocketpay_core.common.alert;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.payment.domain.PaymentAlertLog;
import pocketpaystore.pocketpay_core.payment.domain.PaymentAlertType;
import pocketpaystore.pocketpay_core.payment.domain.AlertSeverity;
import pocketpaystore.pocketpay_core.payment.repository.PaymentAlertLogRepository;

@Service
@RequiredArgsConstructor
public class PaymentAlertLogService {
	private final PaymentAlertLogRepository repository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public void record(PaymentAlertType type, AlertSeverity severity, Long paymentId, Long orderId, String message) {
		PaymentAlertLog alert = repository.save(PaymentAlertLog.create(type, severity, paymentId, orderId, message));
		eventPublisher.publishEvent(new PaymentAlertCreatedEvent(alert.getId(),
				"[" + severity + "] " + type + " (paymentId=" + paymentId + ", orderId=" + orderId + "): " + message));
	}
}
