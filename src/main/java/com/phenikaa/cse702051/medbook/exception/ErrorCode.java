package com.phenikaa.cse702051.medbook.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa xác thực"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền truy cập"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản đang bị khóa"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị xung đột"),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE,"Tệp hoặc nội dung gửi lên quá lớn"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type không được hỗ trợ"),
    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_CONTENT,"Dữ liệu hợp lệ về cú pháp nhưng không thể xử lý"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu, vui lòng thử lại sau"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
