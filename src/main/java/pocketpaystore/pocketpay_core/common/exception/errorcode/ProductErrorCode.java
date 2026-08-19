package pocketpaystore.pocketpay_core.common.exception.errorcode;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

	PRODUCT_NOT_FOUND("PRODUCT_001", "존재하지 않는 상품입니다.", HttpStatus.NOT_FOUND),
	INSUFFICIENT_STOCK("PRODUCT_002", "재고가 부족합니다.", HttpStatus.CONFLICT),
	INVALID_QUANTITY("PRODUCT_003", "수량은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
	INSUFFICIENT_RESERVED_QUANTITY("PRODUCT_004", "예약 수량보다 많은 수량을 처리할 수 없습니다.", HttpStatus.CONFLICT);

	private final String code;
	private final String message;
	private final HttpStatus httpStatus;

}
