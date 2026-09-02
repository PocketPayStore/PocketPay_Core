package pocketpaystore.pocketpay_core.common.alert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SlackNotificationService {
	private final RestClient restClient = RestClient.create();

	@Value("${slack.webhook-url:}")
	private String webhookUrl;

	@Async("slackTaskExecutor")
	public void send(String message) {
		if (webhookUrl == null || webhookUrl.isBlank()) {
			log.info("[Slack] webhook URL이 없어 알림을 로그로 대체: {}", message);
			return;
		}
		try {
			restClient.post().uri(webhookUrl).body(new SlackMessage(message)).retrieve().toBodilessEntity();
		} catch (Exception e) {
			log.error("[Slack] 알림 전송 실패: {}", message, e);
		}
	}

	private record SlackMessage(String text) { }
}
