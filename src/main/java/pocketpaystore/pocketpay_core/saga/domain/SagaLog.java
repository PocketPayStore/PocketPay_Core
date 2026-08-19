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

	public static SagaLog create(Long orderId, SagaStep step) {
		return SagaLog.builder()
				.orderId(orderId)
				.step(step)
				.status(SagaStatus.STARTED)
				.build();
	}

	public void success() {
		this.status = SagaStatus.SUCCESS;
	}

	public void fail(String errorMessage) {
		this.status = SagaStatus.FAILED;
		this.errorMessage = truncate(errorMessage);
	}

	public void compensating() {
		this.status = SagaStatus.COMPENSATING;
	}

	public void compensated() {
		this.status = SagaStatus.COMPENSATED;
	}

	private String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() > 500 ? message.substring(0, 500) : message;
	}

}
