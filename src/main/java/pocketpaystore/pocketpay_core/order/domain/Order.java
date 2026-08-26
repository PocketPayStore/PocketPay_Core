package pocketpaystore.pocketpay_core.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.OrderErrorCode;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Order extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_number", nullable = false, unique = true, length = 50)
	private String orderNumber;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "total_amount", nullable = false)
	private Long totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status;

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
	private String idempotencyKey;

	public static Order create(String orderNumber, Long memberId, Long totalAmount, String idempotencyKey) {
		return Order.builder()
				.orderNumber(orderNumber)
				.memberId(memberId)
				.totalAmount(totalAmount)
				.status(OrderStatus.CREATED)
				.idempotencyKey(idempotencyKey)
				.build();
	}

	public void reserveStock() {
		validateTransition(OrderStatus.CREATED);
		this.status = OrderStatus.STOCK_RESERVED;
	}

	public void markPaymentPending() {
		if (this.status == OrderStatus.EXPIRED) {
			throw new CustomException(OrderErrorCode.ORDER_EXPIRED);
		}
		if (this.status != OrderStatus.STOCK_RESERVED && this.status != OrderStatus.PAYMENT_PENDING) {
			throw new CustomException(OrderErrorCode.INVALID_ORDER_STATE);
		}
		this.status = OrderStatus.PAYMENT_PENDING;
	}

	public void markPaid() {
		validateTransition(OrderStatus.PAYMENT_PENDING);
		this.status = OrderStatus.PAID;
	}

	public void markFailed() {
		if (this.status != OrderStatus.CREATED
				&& this.status != OrderStatus.STOCK_RESERVED
				&& this.status != OrderStatus.PAYMENT_PENDING) {
			throw new CustomException(OrderErrorCode.INVALID_ORDER_STATE);
		}
		this.status = OrderStatus.FAILED;
	}

	public void markCanceled() {
		validateTransition(OrderStatus.PAID);
		this.status = OrderStatus.CANCELED;
	}

	private void validateTransition(OrderStatus expected) {
		if (this.status != expected) {
			throw new CustomException(OrderErrorCode.INVALID_ORDER_STATE);
		}
	}

}
