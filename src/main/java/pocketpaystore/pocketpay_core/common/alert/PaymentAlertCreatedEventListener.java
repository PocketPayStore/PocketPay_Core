package pocketpaystore.pocketpay_core.common.alert;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentAlertCreatedEventListener {
	private final SlackNotificationService slackNotificationService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void sendToSlack(PaymentAlertCreatedEvent event) {
		slackNotificationService.send(event.message());
	}
}
