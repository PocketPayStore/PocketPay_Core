package pocketpaystore.pocketpay_core.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pocketpaystore.pocketpay_core.common.BaseEntity;

@Getter
@Entity
@Table(name = "payment_alert_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAlertLog extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	@Enumerated(EnumType.STRING)
	private PaymentAlertType alertType;

	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private AlertSeverity severity;

	@Column(name = "payment_id")
	private Long paymentId;

	@Column(name = "order_id")
	private Long orderId;

	@Column(nullable = false, length = 500)
	private String message;

	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private PaymentAlertStatus status = PaymentAlertStatus.PENDING;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	private PaymentAlertLog(PaymentAlertType alertType, AlertSeverity severity, Long paymentId, Long orderId, String message) {
		this.alertType = alertType;
		this.severity = severity;
		this.paymentId = paymentId;
		this.orderId = orderId;
		this.message = message;
	}

	public static PaymentAlertLog create(PaymentAlertType alertType, AlertSeverity severity, Long paymentId, Long orderId, String message) {
		return new PaymentAlertLog(alertType, severity, paymentId, orderId, message);
	}

	public void markProcessing() { this.status = PaymentAlertStatus.PROCESSING; }
	public void markResolved() { this.status = PaymentAlertStatus.RESOLVED; this.resolvedAt = LocalDateTime.now(); }
	public void markFailed() { this.status = PaymentAlertStatus.FAILED; this.retryCount++; }
}
