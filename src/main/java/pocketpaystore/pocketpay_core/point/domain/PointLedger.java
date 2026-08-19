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
@Table(name = "point_ledger")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PointLedger extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "order_id")
	private Long orderId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PointLedgerType type;

	@Column(nullable = false)
	private Long amount;

	@Column(name = "balance_after", nullable = false)
	private Long balanceAfter;

	public static PointLedger create(Long memberId, Long orderId, PointLedgerType type, Long amount, Long balanceAfter) {
		return PointLedger.builder()
				.memberId(memberId)
				.orderId(orderId)
				.type(type)
				.amount(amount)
				.balanceAfter(balanceAfter)
				.build();
	}

}
