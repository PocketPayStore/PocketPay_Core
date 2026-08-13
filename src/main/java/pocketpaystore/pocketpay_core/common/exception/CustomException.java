package pocketpaystore.pocketpay_core.common.exception;

import lombok.Getter;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ErrorCode;

@Getter
public class CustomException extends RuntimeException {
    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}