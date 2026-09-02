package pocketpaystore.pocketpay_core.point.domain;

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

@Getter
@Entity
@Table(name = "point_reservation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PointReservation extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "payment_id", nullable = false, unique = true)
	private Long paymentId;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PointReservationStatus status;

	public static PointReservation reserve(Long paymentId, Long memberId, Long amount) {
		return PointReservation.builder()
				.paymentId(paymentId)
				.memberId(memberId)
				.amount(amount)
				.status(PointReservationStatus.RESERVED)
				.build();
	}

	public boolean isReserved() {
		return this.status == PointReservationStatus.RESERVED;
	}

	public void markUsed() {
		this.status = PointReservationStatus.USED;
	}

	public void markReleased() {
		this.status = PointReservationStatus.RELEASED;
	}
}
