package pocketpaystore.pocketpay_core.order.domain;

public enum OrderStatus {
	CREATED,
	STOCK_RESERVED,
	PAYMENT_PENDING,
	PAID,
	FAILED,
	CANCELED,
	PARTIAL_CANCELED,
	EXPIRED
}
