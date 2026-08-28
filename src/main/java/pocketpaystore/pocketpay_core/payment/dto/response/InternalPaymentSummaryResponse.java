package pocketpaystore.pocketpay_core.payment.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import pocketpaystore.pocketpay_core.payment.domain.PaymentStatus;

@Getter
@AllArgsConstructor
public class InternalPaymentSummaryResponse {
	private Long id;
	private String orderNumber;
	private PaymentStatus status;
	private Long amount;
	private LocalDateTime updatedAt;
}
