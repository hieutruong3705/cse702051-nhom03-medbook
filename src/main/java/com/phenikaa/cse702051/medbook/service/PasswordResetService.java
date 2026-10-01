package com.phenikaa.cse702051.medbook.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.exception.BadRequestException;
import com.phenikaa.cse702051.medbook.model.PasswordResetToken;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.PasswordResetTokenRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;

/**
 * Quên / đặt lại mật khẩu (YCCN-04). Token ngẫu nhiên 256 bit, chỉ lưu băm SHA-256, hiệu lực
 * ngắn, dùng một lần. Yêu cầu khôi phục luôn cho cùng một kết quả dù email có tồn tại hay không.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String INVALID_TOKEN = "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn!";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetNotifier notifier;
    private final AuditLogService auditLogService;
    private final int tokenMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetNotifier notifier,
            AuditLogService auditLogService,
            @Value("${medbook.security.reset-token-minutes:15}") int tokenMinutes) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
        this.auditLogService = auditLogService;
        this.tokenMinutes = tokenMinutes;
    }

    /**
     * Tạo token đặt lại cho email (nếu có tài khoản đang hoạt động) và gửi liên kết. Không ném lỗi và
     * không cho biết email có tồn tại hay không.
     */
    @Transactional
    public void requestReset(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmail(normalized)
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .ifPresent(this::issueToken);
    }

    /**
     * Đặt mật khẩu mới bằng token. Token sai, hết hạn, đã dùng đều cho cùng một lỗi 400. Đánh dấu
     * "đã dùng" là thao tác nguyên tử nên hai yêu cầu đồng thời chỉ có một yêu cầu thành công.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadRequestException(INVALID_TOKEN));

        if (tokenRepository.markUsed(token.getId(), now) != 1) {
            throw new BadRequestException(INVALID_TOKEN);
        }

        User user = userRepository.findByIdForUpdate(token.getUserId())
                .orElseThrow(() -> new BadRequestException(INVALID_TOKEN));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1); // vô hiệu hóa mọi phiên cũ
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(now);
        userRepository.save(user);

        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.PASSWORD_RESET, AuditActions.ENTITY_USERS, user.getId())
                .byActor(user.getId()));
    }

    /** Dọn token đã hết hạn từ hơn một ngày. */
    @Scheduled(fixedDelayString = "${medbook.security.revoked-token-cleanup-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        tokenRepository.deleteExpiredBefore(LocalDateTime.now().minusDays(1));
    }

    private void issueToken(User user) {
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.invalidateActive(user.getId(), now); // mỗi người chỉ có một token còn hiệu lực

        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        LocalDateTime expiresAt = now.plusMinutes(tokenMinutes);

        tokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(hash(rawToken))
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());

        try {
            notifier.sendResetLink(user, rawToken, expiresAt);
        } catch (RuntimeException e) {
            // Không để lỗi gửi thư lộ ra ngoài (sẽ cho biết email có tồn tại).
            log.error("Không gửi được liên kết đặt lại mật khẩu cho người dùng id={}", user.getId(), e);
        }
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }
}
