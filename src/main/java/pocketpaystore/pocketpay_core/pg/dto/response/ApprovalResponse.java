package pocketpaystore.pocketpay_core.pg.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalResponse {

	private String paymentKey;
	private String orderId;
	private String status;
	private Long totalAmount;
	private LocalDateTime approvedAt;

}
