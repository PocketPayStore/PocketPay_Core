package pocketpaystore.pocketpay_core.pg.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequest {

	private String pgTransactionId;
	private Long cancelAmount;
	private String reason;
	private String merchantCancelId;

}
