package com.phenikaa.cse702051.medbook.security;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ghi lỗi 401/403 phát sinh trong filter chain của Spring Security theo đúng
 * định dạng {@code ApiError} (timestamp, status, error, code, message, path,
 * details) mà {@code GlobalExceptionHandler} dùng cho lỗi trong controller.
 * Các trường được dựng bằng kiểu đơn giản (chuỗi, số) nên không cần module
 * java.time của Jackson.
 */
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void write(HttpServletRequest request, HttpServletResponse response,
            ErrorCode errorCode, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", errorCode.getStatus().value());
        body.put("error", errorCode.getStatus().getReasonPhrase());
        body.put("code", errorCode.name());
        body.put("message", message == null || message.isBlank() ? errorCode.getDefaultMessage() : message);
        body.put("path", request.getRequestURI());
        body.put("details", Map.of());

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
