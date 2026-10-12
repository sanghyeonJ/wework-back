package com.wework.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponse> toResponse (ErrorCode errorCode) {
        return toResponse(errorCode, errorCode.getMessage());
    }

    private ResponseEntity<ErrorResponse> toResponse (ErrorCode errorCode, String message) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, message));
    }

    // 1. 우리가 직접 던진 예외
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException (BusinessException e) {
        return toResponse(e.getErrorCode(), e.getMessage());
    }

    // 2. @Valid 검증 실패 (필수값 누락, 이메일 형식 오류 등)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException (MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError != null)
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : CommonErrorCode.INVALID_INPUT.getMessage();
        return toResponse(CommonErrorCode.INVALID_INPUT, message);
    }

    // 3. 요청 형식이 잘못된 경우 (JSON 깨짐, ?year=abc, 필수 파라미터 누락)
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest (Exception e) {
        return toResponse(CommonErrorCode.INVALID_INPUT);
    }

    // 4. 없는 주소
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource (NoResourceFoundException e) {
        return toResponse(CommonErrorCode.API_NOT_FOUND);
    }

    // 5. 주소는 맞는데 요청 방식이 틀림 (GET 주소에 POST)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed (HttpRequestMethodNotSupportedException e) {
        return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    // 6. 위에서 못 잡은 나머지 전부
    public ResponseEntity<ErrorResponse> handleException (Exception e) {
        log.error("예상하지 못한 에러", e);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

}
