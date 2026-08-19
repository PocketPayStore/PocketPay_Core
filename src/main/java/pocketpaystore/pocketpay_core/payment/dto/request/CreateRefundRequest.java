package pocketpaystore.pocketpay_core.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateRefundRequest {

	@NotNull
	@Positive
	private Integer quantity;

	private String reason;

}
