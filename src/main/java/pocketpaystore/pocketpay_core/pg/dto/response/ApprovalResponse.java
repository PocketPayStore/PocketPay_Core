package pocketpaystore.pocketpay_core.pg.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 토스페이먼츠 결제 승인 API가 반환하는 Payment 객체 중 우리가 쓰는 필드만 옮겨 담는다.
 * 실제 응답엔 카드 정보 등 훨씬 많은 필드가 있어서 알 수 없는 필드는 무시한다.
 */
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
