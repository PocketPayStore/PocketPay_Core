package pocketpaystore.pocketpay_core.order.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

	private String orderNumber;
	private String status;
	private Long totalAmount;
	private Long productId;
	private int quantity;
	private Long unitPrice;
	private LocalDateTime expiresAt;

	public static OrderResponse from(Order order, OrderItem item) {
		return OrderResponse.builder()
				.orderNumber(order.getOrderNumber())
				.status(order.getStatus().name())
				.totalAmount(order.getTotalAmount())
				.productId(item.getProductId())
				.quantity(item.getQuantity())
				.unitPrice(item.getUnitPrice())
				.expiresAt(order.getExpiresAt())
				.build();
	}

}
