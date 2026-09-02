package pocketpaystore.pocketpay_core.point.domain;

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
import pocketpaystore.pocketpay_core.common.exception.CustomException;
import pocketpaystore.pocketpay_core.common.exception.errorcode.PointErrorCode;

@Getter
@Entity
@Table(name = "point_balance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PointBalance extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false, unique = true)
	private Long memberId;

	@Column(nullable = false)
	private Long balance;

	@Column(name = "reserved_amount", nullable = false)
	private Long reservedAmount;

	public static PointBalance create(Long memberId) {
		return PointBalance.builder()
					.memberId(memberId)
					.balance(0L)
					.reservedAmount(0L)
					.build();
	}

	public Long adjust(Long amount) {
		this.balance += amount;
		return this.balance;
	}

	public Long use(Long amount) {
		if (this.balance < amount) {
			throw new CustomException(PointErrorCode.INSUFFICIENT_POINT_BALANCE);
		}
		this.balance -= amount;
		return this.balance;
	}

	public void reserve(Long amount) {
		if (amount <= 0 || availableBalance() < amount) {
			throw new CustomException(PointErrorCode.INSUFFICIENT_POINT_BALANCE);
		}
		this.reservedAmount += amount;
	}

	public Long confirmReservation(Long amount) {
		if (amount <= 0 || this.reservedAmount < amount || this.balance < amount) {
			throw new CustomException(PointErrorCode.INSUFFICIENT_POINT_BALANCE);
		}
		this.reservedAmount -= amount;
		this.balance -= amount;
		return this.balance;
	}

	public void releaseReservation(Long amount) {
		if (amount <= 0 || this.reservedAmount < amount) {
			throw new CustomException(PointErrorCode.INSUFFICIENT_POINT_BALANCE);
		}
		this.reservedAmount -= amount;
	}

	public Long availableBalance() {
		return this.balance - this.reservedAmount;
	}

}
