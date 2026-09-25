package pocketpaystore.pocketpay_core.point.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import pocketpaystore.pocketpay_core.common.BaseEntity;

@Getter
@Entity
@Table(name = "point_earn_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointEarnLog extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Column(name = "payment_id", nullable = false)
	private Long paymentId;

	@Column(nullable = false)
	private Long amount;

	@Column(name = "reversed_amount", nullable = false)
	private Long reversedAmount = 0L;

	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private PointEarnStatus status = PointEarnStatus.PENDING;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	private PointEarnLog(Long memberId, Long orderId, Long paymentId, Long amount) {
		this.memberId = memberId;
		this.orderId = orderId;
		this.paymentId = paymentId;
		this.amount = amount;
	}

	public static PointEarnLog create(Long memberId, Long orderId, Long paymentId, Long amount) {
		return new PointEarnLog(memberId, orderId, paymentId, amount);
	}

	public boolean isRetryable() {
		return status == PointEarnStatus.PENDING || status == PointEarnStatus.FAILED;
	}

	public long remainingAmount() {
		return this.amount - this.reversedAmount;
	}

	public void reverse(long amount) {
		this.reversedAmount += amount;
	}

	public void markProcessing() {
		this.status = PointEarnStatus.PROCESSING;
	}

	public void markResolved() {
		this.status = PointEarnStatus.RESOLVED;
		this.resolvedAt = LocalDateTime.now();
	}

	public void markFailed() {
		this.status = PointEarnStatus.FAILED;
	}

}
