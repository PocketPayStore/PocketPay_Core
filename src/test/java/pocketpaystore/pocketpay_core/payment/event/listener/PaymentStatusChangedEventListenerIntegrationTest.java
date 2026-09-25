package pocketpaystore.pocketpay_core.payment.event.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentMethod;
import pocketpaystore.pocketpay_core.payment.event.publisher.PaymentStatusEventPublisher;
import pocketpaystore.pocketpay_core.support.RedisTestContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentStatusChangedEventListenerIntegrationTest extends RedisTestContainer {

	@Autowired
	private PaymentStatusEventPublisher publisher;

	@Autowired
	private RedissonClient redissonClient;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Value("${payment-events.channel}")
	private String channel;

	@Test
	void publish_afterCommit_sendsToRedisChannel() throws InterruptedException {
		BlockingQueue<String> received = new LinkedBlockingQueue<>();
		RTopic topic = redissonClient.getTopic(channel, StringCodec.INSTANCE);
		int listenerId = topic.addListener(String.class, (ch, msg) -> received.add(msg));

		try {
			Order order = Order.create("ORD-EVENT-TEST", 1L, 10_000L, "idem-key", LocalDateTime.now().plusMinutes(10));
			Payment payment = Payment.create(1L, PaymentMethod.CARD, "mock-pg", "confirm-key", 10_000L, 0L, "pg-key");

			new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publish(payment, order));

			String message = received.poll(2, TimeUnit.SECONDS);
			assertThat(message).isNotNull();
			assertThat(message).contains("\"orderNumber\":\"ORD-EVENT-TEST\"");
		} finally {
			topic.removeListener(listenerId);
		}
	}

	@Test
	void publish_rollback_doesNotSendToRedis() throws InterruptedException {
		BlockingQueue<String> received = new LinkedBlockingQueue<>();
		RTopic topic = redissonClient.getTopic(channel, StringCodec.INSTANCE);
		int listenerId = topic.addListener(String.class, (ch, msg) -> received.add(msg));

		try {
			Order order = Order.create("ORD-ROLLBACK-TEST", 1L, 10_000L, "idem-key", LocalDateTime.now().plusMinutes(10));
			Payment payment = Payment.create(1L, PaymentMethod.CARD, "mock-pg", "confirm-key", 10_000L, 0L, "pg-key");

			new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				publisher.publish(payment, order);
				status.setRollbackOnly();
			});

			String message = received.poll(500, TimeUnit.MILLISECONDS);
			assertThat(message).isNull();
		} finally {
			topic.removeListener(listenerId);
		}
	}

}
