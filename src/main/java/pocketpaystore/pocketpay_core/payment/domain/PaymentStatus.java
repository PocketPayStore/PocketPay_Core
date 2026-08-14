package pocketpaystore.pocketpay_core.payment.domain;

public enum PaymentStatus {
	READY,
	IN_PROGRESS,
	DONE,
	FAILED,
	CANCELED,
	PARTIAL_CANCELED,
	TIMEOUT_UNKNOWN
}
