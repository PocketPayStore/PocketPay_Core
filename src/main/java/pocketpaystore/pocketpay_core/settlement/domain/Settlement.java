package pocketpaystore.pocketpay_core.settlement.domain;

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

@Getter
@Entity
@Table(name = "settlement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Settlement extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "payment_id", nullable = false)
	private Long paymentId;

	@Column(name = "seller_id", nullable = false)
	private Long sellerId;

	@Column(nullable = false)
	private Long amount;

	@Column(name = "pg_fee_amount", nullable = false)
	private Long pgFeeAmount;

	@Column(name = "platform_fee_amount", nullable = false)
	private Long platformFeeAmount;

	@Column(name = "net_amount", nullable = false)
	private Long netAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SettlementStatus status;

	@Column(name = "settled_at")
	private LocalDateTime settledAt;

}
