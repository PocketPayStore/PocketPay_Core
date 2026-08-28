package pocketpaystore.pocketpay_core.payment.event.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentStatusChangedEvent {
	private String eventId;
	private Long paymentId;
	private Long orderId;
	private String orderNumber;
	private PaymentStatus status;
	private LocalDateTime updatedAt;

	public static PaymentStatusChangedEvent from(Payment payment, Order order) {
		return new PaymentStatusChangedEvent(
				UUID.randomUUID().toString(),
				payment.getId(),
				order.getId(),
				order.getOrderNumber(),
				payment.getStatus(),
				LocalDateTime.now());
	}
}
