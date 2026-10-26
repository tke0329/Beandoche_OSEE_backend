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
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(com.beandoche_osee_backend.domain.avatar.AvatarFailure.class)
    public ResponseEntity<ApiErrorResponse> handleAvatar(com.beandoche_osee_backend.domain.avatar.AvatarFailure failure) {
        return ResponseEntity.status(failure.status()).body(ApiErrorResponse.of(failure.code(), failure.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.TransientDataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleTransientDatabaseFailure(org.springframework.dao.TransientDataAccessException failure) {
        LOG.warn("Database operation requires retry ({})", failure.getClass().getSimpleName());
        return ResponseEntity.status(503).body(ApiErrorResponse.of("TEMPORARILY_UNAVAILABLE", "요청이 겹쳤습니다. 잠시 후 다시 시도해 주세요."));
    }

    @ExceptionHandler(com.beandoche_osee_backend.domain.profile.ProfileFailure.class)
    public ResponseEntity<ApiErrorResponse> handleProfile(com.beandoche_osee_backend.domain.profile.ProfileFailure failure) {
        return ResponseEntity.status(failure.status()).body(ApiErrorResponse.of(failure.code(), failure.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate() {
        return ResponseEntity.status(409).body(ApiErrorResponse.of("RESOURCE_CONFLICT", "이미 사용 중이거나 변경된 정보입니다. 다시 확인해 주세요."));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadSize() {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_PROFILE_IMAGE", "1MB 이하의 이미지를 선택해 주세요."));
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipart() {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_PROFILE_IMAGE", "이미지 요청을 확인해 주세요."));
    }

    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidParameter() {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_REQUEST", "요청 값을 확인해 주세요."));
    }

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
