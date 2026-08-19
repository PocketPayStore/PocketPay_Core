package pocketpaystore.pocketpay_core.pg.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

	private String paymentKey;
	private Long amount;
	private String orderNumber;

}
