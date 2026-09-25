package pocketpaystore.pocketpay_core.payment.event.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_core.notification.service.NotificationService;
import pocketpaystore.pocketpay_core.payment.event.model.PaymentCompletedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedEventListener {

	private final NotificationService notificationService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void notify(PaymentCompletedEvent event) {
		try {
			notificationService.notify(event.paymentId());
		} catch (Exception e) {
			log.error("[PaymentCompleted] 알림 발송 실패: paymentId={}", event.paymentId(), e);
		}
	}

}
