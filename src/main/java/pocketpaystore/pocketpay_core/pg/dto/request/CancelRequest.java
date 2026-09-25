package pocketpaystore.pocketpay_core.pg.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequest {

	private String cancelReason;
	private Long cancelAmount;

}
