package pocketpaystore.pocketpay_core.common.response;

import lombok.Builder;
import lombok.Getter;
import pocketpaystore.pocketpay_core.common.exception.errorcode.ErrorCode;

@Getter
@Builder
public class ErrorResponse {
    private final String code;
    private final String message;

    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }
}