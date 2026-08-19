package pocketpaystore.pocketpay_core.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovePaymentRequest {

	@NotBlank
	private String paymentKey;

	@NotNull
	@PositiveOrZero
	private Long usePointAmount;

	@NotNull
	@PositiveOrZero
	private Long amount;

}
