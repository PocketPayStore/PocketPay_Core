package pocketpaystore.pocketpay_core.common.alert;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CriticalAlertService {

	private final SlackNotificationService slackNotificationService;
	private final AdminAlertPublisher adminAlertPublisher;

	public void alertPgApprovedButPersistFailed(Long orderId, Long paymentId, String pgTransactionId,
												  Long amount, Throwable cause) {
		String message = "[CRITICAL] PG 승인 성공, DB 기록 실패 — 수동 대사 필요: orderId=%d, paymentId=%d, pgTransactionId=%s, amount=%d"
				.formatted(orderId, paymentId, pgTransactionId, amount);
		log.error(message, cause);
		slackNotificationService.send(message);
		adminAlertPublisher.publish(message);
	}

}
