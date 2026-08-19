package pocketpaystore.pocketpay_core.payment.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import pocketpaystore.pocketpay_core.payment.domain.Payment;
import pocketpaystore.pocketpay_core.payment.domain.Refund;

@Getter
@Builder
public class RefundResponse {

	private Long refundId;
	private Long paymentId;
	private String status;
	private Long requestAmount;
	private Long refundableAmountAfter;
	private LocalDateTime processedAt;

	public static RefundResponse of(Refund refund, Payment payment) {
		return RefundResponse.builder()
				.refundId(refund.getId())
				.paymentId(payment.getId())
				.status(refund.getStatus().name())
				.requestAmount(refund.getRequestAmount())
				.refundableAmountAfter(payment.getRefundableAmount())
				.processedAt(refund.getProcessedAt())
				.build();
	}

}
