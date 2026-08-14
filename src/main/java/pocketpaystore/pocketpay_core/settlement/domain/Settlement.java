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

/**
 * 판매자(seller_id)에게 지급할 정산금을 추적한다. 결제 승인 후 사가의 "정산 데이터 적재" 단계에서
 * PENDING으로 한 row가 생기고, 실제 지급(net_amount를 판매자 계좌로 송금)은 별도 배치 잡이 이 테이블을
 * 읽어서 수행한다. net_amount = amount - pg_fee_amount - platform_fee_amount.
 * created_at = 데이터 적재 시각, settled_at = 배치가 실제 지급 처리한 시각으로 서로 다르다.
 * 한 주문은 한 판매자의 상품만 담을 수 있다는 전제라, payment 1건당 settlement 1건이다.
 */
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
