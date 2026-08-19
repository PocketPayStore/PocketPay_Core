package pocketpaystore.pocketpay_core.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pocketpaystore.pocketpay_core.common.BaseEntity;
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PaymentErrorCode;

@Getter
@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Payment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false, length = 20)
	private PaymentMethod paymentMethod;

	@Column(name = "pg_provider", nullable = false, length = 50)
	private String pgProvider;

	@Column(name = "pg_transaction_id", length = 100)
	private String pgTransactionId;

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
	private String idempotencyKey;

	@Column(nullable = false)
	private Long amount;

	@Column(name = "used_point_amount", nullable = false)
	private Long usedPointAmount;

	@Column(name = "refundable_amount", nullable = false)
	private Long refundableAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	@Column(name = "failure_code", length = 50)
	private String failureCode;

	@Column(name = "failure_message", length = 500)
	private String failureMessage;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	public static Payment create(Long orderId, PaymentMethod paymentMethod, String pgProvider, String idempotencyKey,
								  Long amount, Long usedPointAmount, String pgTransactionId) {
		return Payment.builder()
				.orderId(orderId)
				.paymentMethod(paymentMethod)
				.pgProvider(pgProvider)
				.idempotencyKey(idempotencyKey)
				.amount(amount)
				.usedPointAmount(usedPointAmount)
				.pgTransactionId(pgTransactionId)
				.refundableAmount(0L)
				.status(PaymentStatus.READY)
				.build();
	}

	public void toInProgress() {
		validateTransition(PaymentStatus.READY);
		this.status = PaymentStatus.IN_PROGRESS;
	}

	public void toDone() {
		validateTransition(PaymentStatus.IN_PROGRESS);
		this.approvedAt = LocalDateTime.now();
		this.refundableAmount = this.amount;
		this.status = PaymentStatus.DONE;
	}

	public void toFailed(String failureCode, String failureMessage) {
		validateTransition(PaymentStatus.IN_PROGRESS);
		this.status = PaymentStatus.FAILED;
		this.failureCode = failureCode;
		this.failureMessage = failureMessage;
	}

	public void toTimeoutUnknown() {
		validateTransition(PaymentStatus.IN_PROGRESS);
		this.status = PaymentStatus.TIMEOUT_UNKNOWN;
	}

	public void toCanceled() {
		validateTransition(PaymentStatus.DONE);
		this.status = PaymentStatus.CANCELED;
	}

	public void refund(Long refundAmount) {
		if (refundAmount == null || refundAmount <= 0) {
			throw new CustomException(PaymentErrorCode.INVALID_REFUND_AMOUNT);
		}
		if (this.status != PaymentStatus.DONE
				&& this.status != PaymentStatus.PARTIAL_CANCELED
				&& this.status != PaymentStatus.CANCELED) {
			throw new CustomException(PaymentErrorCode.INVALID_PAYMENT_STATE);
		}
		if (this.refundableAmount < refundAmount) {
			throw new CustomException(PaymentErrorCode.EXCESSIVE_REFUND_AMOUNT);
		}
		this.refundableAmount -= refundAmount;
		this.status = (this.refundableAmount == 0) ? PaymentStatus.CANCELED : PaymentStatus.PARTIAL_CANCELED;
	}

	private void validateTransition(PaymentStatus expected) {
		if (this.status != expected) {
			throw new CustomException(PaymentErrorCode.INVALID_PAYMENT_STATE);
		}
	}

}
