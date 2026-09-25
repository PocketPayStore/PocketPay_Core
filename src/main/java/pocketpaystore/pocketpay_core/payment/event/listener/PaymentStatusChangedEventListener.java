package pocketpaystore.pocketpay_core.payment.event.listener;

import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_core.payment.event.model.PaymentStatusChangedEvent;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStatusChangedEventListener {

	private final RedissonClient redissonClient;
	private final ObjectMapper objectMapper;

	@Value("${payment-events.channel}")
	private String channel;

	@Async("sseTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publishToAdminDashboard(PaymentStatusChangedEvent event) {
		try {
			String payload = objectMapper.writeValueAsString(event);
			RTopic topic = redissonClient.getTopic(channel, StringCodec.INSTANCE);
			topic.publish(payload);
		} catch (Exception e) {
			log.error("[PaymentStatusEvent] Redis 발행 실패: paymentId={}, status={}",
					event.getPaymentId(), event.getStatus(), e);
		}
	}
}
