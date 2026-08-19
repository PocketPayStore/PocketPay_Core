package pocketpaystore.pocketpay_core.common.exception.errorcode;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

	PAYMENT_NOT_FOUND("PAYMENT_001", "존재하지 않는 결제입니다.", HttpStatus.NOT_FOUND),
	EXCESSIVE_REFUND_AMOUNT("PAYMENT_002", "환불 가능 금액을 초과했습니다.", HttpStatus.CONFLICT),
	INVALID_REFUND_AMOUNT("PAYMENT_003", "환불 금액은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
	INVALID_PAYMENT_STATE("PAYMENT_004", "결제 상태를 변경할 수 없습니다.", HttpStatus.BAD_REQUEST),
	INVALID_REFUND_STATE("PAYMENT_005", "환불 상태를 변경할 수 없습니다.", HttpStatus.BAD_REQUEST),
	INVALID_POINT_USE_AMOUNT("PAYMENT_006", "사용 포인트는 0 이상, 주문 금액 이하여야 합니다.", HttpStatus.BAD_REQUEST),
	AUTHORIZED_AMOUNT_MISMATCH("PAYMENT_007", "결제 인증된 금액과 승인 요청 금액이 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
	PAYMENT_ALREADY_IN_PROGRESS("PAYMENT_008", "이 주문에 대해 이미 처리 중인 결제 시도가 있습니다.", HttpStatus.CONFLICT);

	private final String code;
	private final String message;
	private final HttpStatus httpStatus;

}
