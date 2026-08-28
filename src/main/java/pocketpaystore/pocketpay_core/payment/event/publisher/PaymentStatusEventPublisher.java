package pocketpaystore.pocketpay_core.payment.event.publisher;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.event.model.PaymentStatusChangedEvent;

@Component
@RequiredArgsConstructor
public class PaymentStatusEventPublisher {
	private final ApplicationEventPublisher eventPublisher;

	public void publish(Payment payment, Order order) {
		eventPublisher.publishEvent(PaymentStatusChangedEvent.from(payment, order));
	}
}
