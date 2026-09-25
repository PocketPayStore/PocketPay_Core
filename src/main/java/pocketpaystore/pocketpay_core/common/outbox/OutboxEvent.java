package pocketpaystore.pocketpay_core.common.outbox;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import pocketpaystore.pocketpay_core.common.BaseEntity;

/**
 * 상태 변경과 같은 트랜잭션에 커밋되는 발행 대기 이벤트. 도메인 상태를 바꾸는 트랜잭션 안에서
 * 이 행을 같이 커밋해두면, 그 트랜잭션이 실제로 성공했을 때만 이벤트가 존재하게 된다
 * (커밋 전 발행 없음). 실제 발행(릴레이)은 이 레포가 아니라 PocketPay_Batch의
 * outboxRelayJob이 이 테이블을 직접 읽고 갱신하며 수행한다 — Core는 쓰기 전용이다.
 */
@Getter
@Entity
@Table(name = "outbox_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "aggregate_type", nullable = false, length = 50)
	private OutboxAggregateType aggregateType;

	@Column(name = "aggregate_id", nullable = false)
	private Long aggregateId;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(nullable = false, columnDefinition = "json")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OutboxEventStatus status;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	private OutboxEvent(OutboxAggregateType aggregateType, Long aggregateId, String eventType, String payload) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = OutboxEventStatus.PENDING;
	}

	public static OutboxEvent create(OutboxAggregateType aggregateType, Long aggregateId, String eventType, String payload) {
		return new OutboxEvent(aggregateType, aggregateId, eventType, payload);
	}

}
