package pocketpaystore.pocketpay_core.common.exception.errorcode;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

	ORDER_NOT_FOUND("ORDER_001", "존재하지 않는 주문입니다.", HttpStatus.NOT_FOUND),
	EMPTY_ORDER_ITEMS("ORDER_002", "주문 항목이 비어 있습니다.", HttpStatus.BAD_REQUEST),
	INVALID_ORDER_STATE("ORDER_003", "주문 상태를 변경할 수 없습니다.", HttpStatus.BAD_REQUEST),
	ORDER_EXPIRED("ORDER_004", "주문이 만료되어 결제를 진행할 수 없습니다.", HttpStatus.CONFLICT);

	private final String code;
	private final String message;
	private final HttpStatus httpStatus;

}
