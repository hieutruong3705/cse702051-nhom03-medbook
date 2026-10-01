package com.phenikaa.cse702051.medbook.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
        return buildResponse(exception.getErrorCode(), exception.getMessage(), request.getRequestURI());
    }

    /** Tài khoản bị khóa tạm thời: kèm thời điểm mở khóa để giao diện hiển thị. */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> handleAccountLocked(AccountLockedException exception, HttpServletRequest request) {
        Map<String, String> details = exception.getLockedUntil() == null
                ? Map.of()
                : Map.of("lockedUntil", exception.getLockedUntil().toString());
        ApiError body = ApiError.of(ErrorCode.ACCOUNT_LOCKED, exception.getMessage(), request.getRequestURI(), details);
        return ResponseEntity.status(ErrorCode.ACCOUNT_LOCKED.getStatus()).body(body);
    }

    /** Lỗi nghiệp vụ gắn với một trường của form (ví dụ mật khẩu cũ sai) → 400 kèm details. */
    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiError> handleFieldValidation(FieldValidationException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of(ErrorCode.VALIDATION_FAILED, exception.getMessage(), request.getRequestURI(),
                exception.getDetails());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> details.put(error.getObjectName(), error.getDefaultMessage()));

        ApiError body = ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getDefaultMessage(),
                request.getRequestURI(),
                details);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus()).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getConstraintViolations()
                .forEach(violation -> details.put(violation.getPropertyPath().toString(), violation.getMessage()));

        ApiError body = ApiError.of(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getDefaultMessage(),
                request.getRequestURI(),
                details);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus()).body(body);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        return buildResponse(ErrorCode.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
    }

    /**
     * Các service cũ ném IllegalArgumentException cho cả lỗi quyền lẫn "không tìm
     * thấy". Vẫn trả 400 để không phá hợp đồng hiện tại, nhưng ghi WARN kèm nơi
     * ném để từng module chuyển sang ForbiddenException/ResourceNotFoundException.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        StackTraceElement origin = exception.getStackTrace().length > 0 ? exception.getStackTrace()[0] : null;
        log.warn("IllegalArgumentException tại {} {}: {} (nguồn: {})",
                request.getMethod(), request.getRequestURI(), exception.getMessage(), origin);
        return buildResponse(ErrorCode.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request) {
        return buildResponse(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này!",
                request.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(
            AuthenticationException exception,
            HttpServletRequest request) {
        return buildResponse(ErrorCode.UNAUTHORIZED, "Bạn chưa đăng nhập hoặc phiên đã hết hạn!",
                request.getRequestURI());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return buildResponse(ErrorCode.PAYLOAD_TOO_LARGE, "Tệp tải lên vượt quá dung lượng cho phép (tối đa 10 MB)!",
                request.getRequestURI());
    }

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ApiError> handleNoResource(Exception exception, HttpServletRequest request) {
        return buildResponse(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đường dẫn yêu cầu!",
                request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return buildResponse(ErrorCode.CONFLICT, ErrorCode.CONFLICT.getDefaultMessage(), request.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return buildResponse(ErrorCode.METHOD_NOT_ALLOWED, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        return buildResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request) {
        ErrorCode errorCode = switch (exception.getStatusCode().value()) {
            case 400 -> ErrorCode.BAD_REQUEST;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.CONFLICT;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 422 -> ErrorCode.UNPROCESSABLE_ENTITY;
            default -> ErrorCode.INTERNAL_ERROR;
        };
        return buildResponse(errorCode, exception.getReason(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Lỗi không mong đợi tại {} {}", request.getMethod(), request.getRequestURI(), exception);
        return buildResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                request.getRequestURI());
    }

    private ResponseEntity<ApiError> buildResponse(ErrorCode errorCode, String message, String path) {
        ApiError body = ApiError.of(errorCode, message, path);
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }
}
