package pocketpaystore.pocketpay_core.common.exception.errorcode;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PointErrorCode implements ErrorCode {

	POINT_BALANCE_NOT_FOUND("POINT_001", "포인트 잔액이 존재하지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
	INSUFFICIENT_POINT_BALANCE("POINT_002", "포인트 잔액이 부족합니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String message;
	private final HttpStatus httpStatus;

}
