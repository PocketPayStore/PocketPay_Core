package pocketpaystore.pocketpay_core.common.alert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackNotificationService {
	private final RestClient restClient = RestClient.create();
	private final PaymentAlertLogService paymentAlertLogService;

	@Value("${slack.webhook-url:}")
	private String webhookUrl;

	@Async("slackTaskExecutor")
	public void send(Long alertId, String message) {
		if (webhookUrl == null || webhookUrl.isBlank()) {
			log.info("[Slack] webhook URL이 없어 알림을 로그로 대체: {}", message);
			return;
		}
		paymentAlertLogService.markProcessing(alertId);
		try {
			restClient.post().uri(webhookUrl).body(new SlackMessage(message)).retrieve().toBodilessEntity();
			paymentAlertLogService.markResolved(alertId);
		} catch (Exception e) {
			log.error("[Slack] 알림 전송 실패: {}", message, e);
			paymentAlertLogService.markFailed(alertId);
		}
	}

	private record SlackMessage(String text) { }
}
