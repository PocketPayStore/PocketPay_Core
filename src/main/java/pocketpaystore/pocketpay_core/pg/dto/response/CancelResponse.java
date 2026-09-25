package pocketpaystore.pocketpay_core.pg.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 토스페이먼츠 결제 취소 API가 반환하는 Payment 객체(cancels 배열 포함) 중 우리가 쓰는 필드만
 * 옮겨 담는다. 알 수 없는 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CancelResponse {

	private String paymentKey;
	private String status;

}
