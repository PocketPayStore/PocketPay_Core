package pocketpaystore.pocketpay_core.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pocketpaystore.pocketpay_core.common.BaseEntity;

@Getter
@Entity
@Table(name = "order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OrderItem extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false)
	private Long unitPrice;

	public static OrderItem create(Long orderId, Long productId, int quantity, Long unitPrice) {
		return OrderItem.builder()
				.orderId(orderId)
				.productId(productId)
				.quantity(quantity)
				.unitPrice(unitPrice)
				.build();
	}

}
