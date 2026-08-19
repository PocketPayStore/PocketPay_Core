package pocketpaystore.pocketpay_core.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import pocketpaystore.pocketpay_core.common.response.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.error("[Common] 비즈니스 예외: {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ErrorResponse.of(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        log.error("[Common] 유효성 검사 실패: {}", e.getMessage());
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder().code("VALIDATION_ERROR").message(message).build());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        log.error("[Common] 유효성 검사 실패: {}", e.getMessage());
        if (e.getCause() instanceof CustomException customException) {
            return ResponseEntity
                    .status(customException.getErrorCode().getHttpStatus())
                    .body(ErrorResponse.of(customException.getErrorCode()));
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder().code("VALIDATION_ERROR").message(e.getMessage()).build());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException e) {
        log.error("[Common] 필수 헤더 누락: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder().code("VALIDATION_ERROR")
                        .message(e.getHeaderName() + " 헤더가 필요합니다.").build());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException e) {
        log.error("[Common] 낙관적 락 충돌(재시도 소진): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder().code("CONCURRENT_UPDATE_CONFLICT")
                        .message("다른 요청과 충돌해 처리하지 못했습니다. 잠시 후 다시 시도해주세요.").build());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder().code("NOT_FOUND").message("요청한 리소스를 찾을 수 없습니다.").build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[Common] 처리되지 않은 예외: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.builder().code("INTERNAL_SERVER_ERROR").message("서버 내부 오류가 발생했습니다.").build());
    }
}