package pocketpaystore.pocketpay_core.pg.domain;

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

@Getter
@Entity
@Table(name = "pg_callback_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PgCallbackLog extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "pg_transaction_id", length = 100)
	private String pgTransactionId;

	@Column(nullable = false, columnDefinition = "json")
	private String payload;

	@Column(name = "signature_valid", nullable = false)
	private boolean signatureValid;

	@Column(nullable = false)
	private boolean processed;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	public static PgCallbackLog create(String pgTransactionId, String payload, boolean signatureValid) {
		return PgCallbackLog.builder()
				.pgTransactionId(pgTransactionId)
				.payload(payload)
				.signatureValid(signatureValid)
				.processed(false)
				.retryCount(0)
				.build();
	}

}
