package com.phenikaa.cse702051.medbook.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
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

    /** Tên gói Java hoặc dấu vết ngoại lệ lọt vào thông điệp lỗi. */
    private static final Pattern INTERNAL_DETAIL = Pattern.compile(
            "\\b(java|javax|jakarta|org|com|net|io)\\.[a-z][\\w.]*\\.[A-Z]\\w*|Exception\\b|\\bat \\w+\\.");

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

    /** Vượt giới hạn tần suất: 429 kèm header {@code Retry-After} (giây). */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException exception,
            HttpServletRequest request) {
        String retryAfter = String.valueOf(exception.getRetryAfterSeconds());
        ApiError body = ApiError.of(ErrorCode.TOO_MANY_REQUESTS, exception.getMessage(), request.getRequestURI(),
                Map.of("retryAfterSeconds", retryAfter));
        return ResponseEntity.status(ErrorCode.TOO_MANY_REQUESTS.getStatus())
                .header("Retry-After", retryAfter)
                .body(body);
    }

    /** Lỗi nghiệp vụ gắn với một trường của form (ví dụ mật khẩu cũ sai) → 400 kèm details. */
    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiError> handleFieldValidation(FieldValidationException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of(ErrorCode.VALIDATION_FAILED, exception.getMessage(), request.getRequestURI(),
                exception.getDetails());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus()).body(body);
    }

    /** Giá trị trùng với bản ghi đã có (mã, tên đăng nhập, email...) → 409 kèm details theo trường. */
    @ExceptionHandler(FieldConflictException.class)
    public ResponseEntity<ApiError> handleFieldConflict(FieldConflictException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of(ErrorCode.CONFLICT, exception.getMessage(), request.getRequestURI(),
                exception.getDetails());
        return ResponseEntity.status(ErrorCode.CONFLICT.getStatus()).body(body);
    }

    /**
     * Hai yêu cầu cùng sửa một bản ghi (khóa lạc quan {@code @Version}) hoặc chờ khóa quá lâu. Service nên bắt và
     * báo thông điệp cụ thể hơn; đây là lớp chặn cuối để xung đột không bao giờ thành lỗi 500.
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiError> handleConcurrencyFailure(
            ConcurrencyFailureException exception,
            HttpServletRequest request) {
        return buildResponse(ErrorCode.CONFLICT,
                "Dữ liệu vừa được người khác thay đổi. Vui lòng tải lại và thử lại!", request.getRequestURI());
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
        // Thông điệp gốc của Spring chứa tên lớp Java và chi tiết bộ phân tích JSON: không trả ra ngoài.
        String message;
        if (exception instanceof MissingServletRequestParameterException missing) {
            message = "Thiếu tham số bắt buộc: " + missing.getParameterName();
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            message = "Tham số '" + mismatch.getName() + "' không đúng định dạng!";
        } else {
            message = "Nội dung yêu cầu không đọc được: sai cú pháp JSON hoặc sai kiểu dữ liệu!";
        }
        return buildResponse(ErrorCode.BAD_REQUEST, message, request.getRequestURI());
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
        return buildResponse(ErrorCode.BAD_REQUEST, safeMessage(exception.getMessage(), ErrorCode.BAD_REQUEST),
                request.getRequestURI());
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
        return buildResponse(ErrorCode.METHOD_NOT_ALLOWED,
                "Phương thức " + request.getMethod() + " không được hỗ trợ cho đường dẫn này!",
                request.getRequestURI());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        return buildResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Kiểu nội dung (Content-Type) của yêu cầu không được hỗ trợ!", request.getRequestURI());
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
        return buildResponse(errorCode, safeMessage(exception.getReason(), errorCode), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Lỗi không mong đợi tại {} {}", request.getMethod(), request.getRequestURI(), exception);
        return buildResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                request.getRequestURI());
    }

    /**
     * Thông điệp do mã của ứng dụng viết thì giữ nguyên; thông điệp rỗng hoặc có dấu hiệu sinh từ thư viện (chứa
     * tên gói Java, ví dụ {@code No enum constant com...}) được thay bằng thông điệp chung để không lộ cấu trúc
     * bên trong.
     */
    private static String safeMessage(String message, ErrorCode errorCode) {
        if (message == null || message.isBlank() || INTERNAL_DETAIL.matcher(message).find()) {
            return errorCode.getDefaultMessage();
        }
        return message;
    }

    private ResponseEntity<ApiError> buildResponse(ErrorCode errorCode, String message, String path) {
        ApiError body = ApiError.of(errorCode, message, path);
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }
}
