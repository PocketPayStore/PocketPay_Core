package pocketpaystore.pocketpay_core.common.alert;

import java.time.LocalDateTime;

import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAlertPublisher {

	private final RedissonClient redissonClient;
	private final ObjectMapper objectMapper;

	@Value("${admin-alerts.channel:admin:critical-alert}")
	private String channel;

	@Async("slackTaskExecutor")
	public void publish(String message) {
		try {
			String payload = objectMapper.writeValueAsString(new AdminAlertMessage(message, LocalDateTime.now()));
			RTopic topic = redissonClient.getTopic(channel, StringCodec.INSTANCE);
			topic.publish(payload);
		} catch (Exception e) {
			log.error("[AdminAlert] Redis 발행 실패: message={}", message, e);
		}
	}

	private record AdminAlertMessage(String message, LocalDateTime occurredAt) { }

}
