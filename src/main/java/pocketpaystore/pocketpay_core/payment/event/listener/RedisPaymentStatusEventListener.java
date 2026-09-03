package pocketpaystore.pocketpay_core.payment.event.listener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;
import pocketpaystore.pocketpay_core.payment.event.model.PaymentStatusChangedEvent;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class RedisPaymentStatusEventListener {
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	@Value("${payment-events.channel}")
	private String channel;

	public RedisPaymentStatusEventListener(
			@Qualifier("paymentEventRedisTemplate") StringRedisTemplate redisTemplate,
			ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	@Async("paymentEventTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(PaymentStatusChangedEvent event) {
		try {
			redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(event));
		} catch (Exception e) {
			log.error("[PaymentEvent] Redis 발행 실패: eventId={}, paymentId={}", event.getEventId(), event.getPaymentId(), e);
		}
	}
}
