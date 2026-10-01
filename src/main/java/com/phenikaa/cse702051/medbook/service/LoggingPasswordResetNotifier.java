package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.model.User;

/**
 * Bản mặc định khi chưa cấu hình email: ghi liên kết đặt lại vào log để phát triển/demo.
 * CHỈ dùng ở môi trường dev — token trong log là thông tin bí mật. Bản gửi email thật thay thế
 * bằng cách khai báo một bean {@link PasswordResetNotifier} khác được đánh dấu {@code @Primary}.
 */
@Service
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    private final String frontendBaseUrl;

    public LoggingPasswordResetNotifier(
            @Value("${medbook.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void sendResetLink(User user, String rawToken, LocalDateTime expiresAt) {
        log.info("[DEV] Liên kết đặt lại mật khẩu cho '{}' (hết hạn {}): {}/#/reset-password?token={}",
                user.getUsername(), expiresAt, frontendBaseUrl, rawToken);
    }
}
