package pocketpaystore.pocketpay_core.saga.domain;

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

/**
 * append-only 사가 진행 이력. 같은 (order_id, step)이라도 상태가 바뀔 때마다(STARTED → SUCCESS/FAILED
 * → COMPENSATING → COMPENSATED) 기존 row를 UPDATE하지 않고 새 row를 insert한다. 그래야 각 전이가
 * 언제 일어났는지(created_at) 개별적으로 남아서, 실패~보상 완료까지 걸린 시간을 정확히 추적할 수 있다.
 */
@Getter
@Entity
@Table(name = "saga_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SagaLog extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SagaStep step;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SagaStatus status;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

}
