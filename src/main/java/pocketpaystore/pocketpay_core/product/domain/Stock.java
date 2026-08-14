package pocketpaystore.pocketpay_core.product.domain;

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
 * product와 1:1인 재고 테이블. PK는 다른 테이블처럼 별도 auto_increment id고, product_id는
 * UNIQUE 제약으로 1:1을 보장한다.
 * 가용 재고 = total_quantity - reserved_quantity - sold_quantity.
 * 이 계산 로직은 엔티티가 아니라 도메인 서비스 한 곳에만 구현한다 (도메인 규칙 4번).
 * 현재는 비관적 락(SELECT ... FOR UPDATE)으로 동시성을 제어한다. 낙관적 락(version)은 단계 5(락 전략
 * 실험)에서 다시 도입한다.
 */
@Getter
@Entity
@Table(name = "stock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Stock extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false, unique = true)
	private Long productId;

	@Column(name = "total_quantity", nullable = false)
	private int totalQuantity;

	@Column(name = "reserved_quantity", nullable = false)
	private int reservedQuantity;

	@Column(name = "sold_quantity", nullable = false)
	private int soldQuantity;

}
