package pocketpaystore.pocketpay_core.pg.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatusResponse {

	private String pgTransactionId;
	private String status;
	private LocalDateTime approvedAt;

}
