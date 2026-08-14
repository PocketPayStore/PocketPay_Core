package pocketpaystore.pocketpay_core.saga.domain;

public enum SagaStatus {
	STARTED,
	SUCCESS,
	FAILED,
	COMPENSATING,
	COMPENSATED
}
