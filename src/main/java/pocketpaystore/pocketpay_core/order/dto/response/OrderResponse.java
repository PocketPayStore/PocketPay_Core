package pocketpaystore.pocketpay_core.order.dto.response;

import lombok.Builder;
import lombok.Getter;
import pocketpaystore.pocketpay_core.order.domain.Order;
import pocketpaystore.pocketpay_core.order.domain.OrderItem;

@Getter
@Builder
public class OrderResponse {

	private String orderNumber;
	private String status;
	private Long totalAmount;
	private Long productId;
	private int quantity;
	private Long unitPrice;

	public static OrderResponse from(Order order, OrderItem item) {
		return OrderResponse.builder()
				.orderNumber(order.getOrderNumber())
				.status(order.getStatus().name())
				.totalAmount(order.getTotalAmount())
				.productId(item.getProductId())
				.quantity(item.getQuantity())
				.unitPrice(item.getUnitPrice())
				.build();
	}

}
