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
@Table(name = "refund")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Refund extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "payment_id", nullable = false)
	private Long paymentId;

	@Column(name = "request_amount", nullable = false)
	private Long requestAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RefundStatus status;

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
	private String idempotencyKey;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "processed_at")
	private LocalDateTime processedAt;

	public static Refund create(Long paymentId, Long requestAmount, String idempotencyKey) {
		return Refund.builder()
				.paymentId(paymentId)
				.requestAmount(requestAmount)
				.status(RefundStatus.REQUESTED)
				.idempotencyKey(idempotencyKey)
				.requestedAt(LocalDateTime.now())
				.build();
	}

	public void toProcessing() {
		validateTransition(RefundStatus.REQUESTED);
		this.status = RefundStatus.PROCESSING;
	}

	public void toCompleted() {
		validateTransition(RefundStatus.PROCESSING);
		this.status = RefundStatus.COMPLETED;
		this.processedAt = LocalDateTime.now();
	}

	public void toFailed() {
		validateTransition(RefundStatus.PROCESSING);
		this.status = RefundStatus.FAILED;
		this.processedAt = LocalDateTime.now();
	}

	public void toRejected() {
		validateTransition(RefundStatus.REQUESTED);
		this.status = RefundStatus.REJECTED;
		this.processedAt = LocalDateTime.now();
	}

	private void validateTransition(RefundStatus expected) {
		if (this.status != expected) {
			throw new CustomException(PaymentErrorCode.INVALID_REFUND_STATE);
		}
	}

}
