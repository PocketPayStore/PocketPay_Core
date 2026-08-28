package pocketpaystore.pocketpay_core.payment.event.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;
import pocketpaystore.pocketpay_core.payment.event.model.PaymentStatusChangedEvent;
import tools.jackson.databind.ObjectMapper;

class RedisPaymentStatusEventListenerTest {

	@Test
	void publishesSerializedEvent() throws Exception {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ObjectMapper objectMapper = mock(ObjectMapper.class);
		RedisPaymentStatusEventListener listener = new RedisPaymentStatusEventListener(redis, objectMapper);
		setChannel(listener, "payment:test");
		PaymentStatusChangedEvent event = createEvent();
		when(objectMapper.writeValueAsString(event)).thenReturn("{}");

		listener.publish(event);

		verify(redis).convertAndSend("payment:test", "{}");
	}

	@Test
	void isolatesRedisFailure() throws Exception {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ObjectMapper objectMapper = mock(ObjectMapper.class);
		RedisPaymentStatusEventListener listener = new RedisPaymentStatusEventListener(redis, objectMapper);
		setChannel(listener, "payment:test");
		PaymentStatusChangedEvent event = createEvent();
		when(objectMapper.writeValueAsString(event)).thenThrow(new RuntimeException("redis unavailable"));

		assertThatCode(() -> listener.publish(event)).doesNotThrowAnyException();
	}

	private void setChannel(RedisPaymentStatusEventListener listener, String channel) throws Exception {
		Field field = RedisPaymentStatusEventListener.class.getDeclaredField("channel");
		field.setAccessible(true);
		field.set(listener, channel);
	}

	private PaymentStatusChangedEvent createEvent() {
		Payment payment = mock(Payment.class);
		Order order = mock(Order.class);
		when(payment.getId()).thenReturn(1L);
		when(payment.getStatus()).thenReturn(PaymentStatus.DONE);
		when(order.getId()).thenReturn(1L);
		when(order.getOrderNumber()).thenReturn("ORDER-1");
		return PaymentStatusChangedEvent.from(payment, order);
	}
}
