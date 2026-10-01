package com.phenikaa.cse702051.medbook.support;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.service.PasswordResetNotifier;

/**
 * Thay bản ghi log mặc định trong test: giữ lại token gốc của lần gửi gần nhất cho từng email để
 * test có thể dùng token đó (token gốc không bao giờ nằm trong CSDL hay phản hồi API).
 */
@Component
@Primary
public class CapturingPasswordResetNotifier implements PasswordResetNotifier {

    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> expiries = new ConcurrentHashMap<>();

    @Override
    public void sendResetLink(User user, String rawToken, LocalDateTime expiresAt) {
        tokens.put(user.getEmail(), rawToken);
        expiries.put(user.getEmail(), expiresAt);
    }

    public Optional<String> lastTokenFor(String email) {
        return Optional.ofNullable(tokens.get(email));
    }

    public Optional<LocalDateTime> lastExpiryFor(String email) {
        return Optional.ofNullable(expiries.get(email));
    }
}
