package com.beandoche_osee_backend.global.error;

import java.util.List;

import com.beandoche_osee_backend.domain.auth.service.InvalidSocialTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiErrorResponse.FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "INVALID_REQUEST",
                "요청 값을 확인해 주세요.",
                fieldErrors));
    }

    @ExceptionHandler(InvalidSocialTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidSocialToken() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.of(
                "SOCIAL_LOGIN_FAILED",
                "Google 로그인에 실패했습니다."));
    }

    private ApiErrorResponse.FieldErrorResponse toFieldError(FieldError error) {
        return new ApiErrorResponse.FieldErrorResponse(error.getField(), error.getDefaultMessage());
    }
}
