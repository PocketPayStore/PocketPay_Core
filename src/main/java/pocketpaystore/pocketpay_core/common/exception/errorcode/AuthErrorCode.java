package pocketpaystore.pocketpay_core.common.exception.errorcode;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

	MEMBER_NOT_FOUND("AUTH_001", "존재하지 않는 회원입니다.", HttpStatus.NOT_FOUND),
	INVALID_PASSWORD("AUTH_002", "비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
	INVALID_TOKEN("AUTH_003", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED);

	private final String code;
	private final String message;
	private final HttpStatus httpStatus;

}
