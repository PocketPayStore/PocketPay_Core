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

/**
 * member와 1:1인 잔액 테이블. PK는 다른 테이블처럼 별도 auto_increment id고, member_id는
 * UNIQUE 제약으로 1:1을 보장한다.
 * balance 갱신은 이 테이블에서만 하고, 모든 변동 이력은 {@link PointLedger}에 append-only로 남긴다.
 * 현재는 비관적 락(SELECT ... FOR UPDATE)으로 동시성을 제어한다. 낙관적 락(version)은 단계 5(락 전략
 * 실험)에서 다시 도입한다.
 */
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

}
