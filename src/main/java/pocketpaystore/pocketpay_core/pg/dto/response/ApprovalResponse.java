package pocketpaystore.pocketpay_core.pg.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalResponse {

	private String pgTransactionId;
	private String resultCode;
	private String resultMessage;
	private LocalDateTime approvedAt;

}
