package pocketpaystore.pocketpay_core.payment.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;

@Getter
@AllArgsConstructor
public class InternalPaymentResponse {
	private Long id;
	private Long orderId;
	private String orderNumber;
	private PaymentStatus status;
	private Long amount;
	private Long usedPointAmount;
	private String pgTransactionId;
	private String failureCode;
	private String failureMessage;
	private LocalDateTime approvedAt;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
