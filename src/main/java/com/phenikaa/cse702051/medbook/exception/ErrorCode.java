package com.phenikaa.cse702051.medbook.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Request khong hop le"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Du lieu dau vao khong hop le"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chua xac thuc"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Khong co quyen truy cap"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Khong tim thay tai nguyen"),
    CONFLICT(HttpStatus.CONFLICT, "Du lieu bi xung dot"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type khong duoc ho tro"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phuong thuc HTTP khong duoc ho tro"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Loi he thong");

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
