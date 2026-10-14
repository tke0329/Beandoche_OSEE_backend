package com.beandoche_osee_backend.global.error;

import java.util.List;

import com.beandoche_osee_backend.domain.auth.service.InvalidSocialTokenException;
import com.beandoche_osee_backend.domain.auth.service.SocialLoginNotAllowedException;
import com.beandoche_osee_backend.domain.auth.service.SocialProviderUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage() {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "INVALID_REQUEST",
                "요청 값을 확인해 주세요."));
    }

    @ExceptionHandler({InvalidSocialTokenException.class, SocialLoginNotAllowedException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidSocialToken() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.of(
                "SOCIAL_LOGIN_FAILED",
                "Google 로그인에 실패했습니다."));
    }

    @ExceptionHandler(SocialProviderUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleSocialProviderUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiErrorResponse.of(
                "SOCIAL_PROVIDER_UNAVAILABLE",
                "Google 로그인 서비스를 잠시 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."));
    }

    private ApiErrorResponse.FieldErrorResponse toFieldError(FieldError error) {
        return new ApiErrorResponse.FieldErrorResponse(error.getField(), error.getDefaultMessage());
    }
}
