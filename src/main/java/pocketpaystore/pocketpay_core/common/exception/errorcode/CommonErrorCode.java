package pocketpaystore.pocketpay_core.common.exception.errorcode;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

	DUPLICATE_REQUEST("COMMON_001", "이미 처리 중이거나 처리된 요청입니다.", HttpStatus.CONFLICT),
	REQUEST_TIMEOUT("COMMON_002", "다른 요청이 처리 중입니다. 잠시 후 다시 시도해주세요.", HttpStatus.CONFLICT),
	IDEMPOTENCY_KEY_MISMATCH("COMMON_003", "이 Idempotency-Key는 다른 요청에 이미 사용되었습니다.", HttpStatus.CONFLICT);

	private final String code;
	private final String message;
	private final HttpStatus httpStatus;

}
