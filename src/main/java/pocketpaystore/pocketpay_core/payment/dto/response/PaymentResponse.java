package pocketpaystore.pocketpay_core.payment.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pocketpaystore.pocketpay_core.payment.domain.Payment;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

	private Long id;
	private String orderNumber;
	private String status;
	private Long amount;
	private Long usedPointAmount;
	private String pgTransactionId;
	private String failureCode;
	private String failureMessage;
	private LocalDateTime approvedAt;

	public static PaymentResponse from(Payment payment, String orderNumber) {
		return PaymentResponse.builder()
				.id(payment.getId())
				.orderNumber(orderNumber)
				.status(payment.getStatus().name())
				.amount(payment.getAmount())
				.usedPointAmount(payment.getUsedPointAmount())
				.pgTransactionId(payment.getPgTransactionId())
				.failureCode(payment.getFailureCode())
				.failureMessage(payment.getFailureMessage())
				.approvedAt(payment.getApprovedAt())
				.build();
	}

}
