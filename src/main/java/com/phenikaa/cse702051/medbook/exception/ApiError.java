package com.phenikaa.cse702051.medbook.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.phenikaa.cse702051.medbook.config.RequestIdFilter;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, String> details,
        /** Mã định danh của yêu cầu, trùng với tiêu đề {@code X-Request-Id} và với log phía máy chủ. */
        String requestId
) {
    public static ApiError of(ErrorCode errorCode, String message, String path) {
        return of(errorCode, message, path, Map.of());
    }

    public static ApiError of(ErrorCode errorCode, String message, String path, Map<String, String> details) {
        HttpStatus status = errorCode.getStatus();
        String responseMessage = message == null || message.isBlank()
                ? errorCode.getDefaultMessage()
                : message;

        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                errorCode.name(),
                responseMessage,
                path,
                Map.copyOf(details),
                RequestIdFilter.currentRequestId());
    }
}
