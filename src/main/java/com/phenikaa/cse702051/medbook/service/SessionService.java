package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.config.JwtUtil.JwtClaims;
import com.phenikaa.cse702051.medbook.model.RevokedToken;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.RevokedTokenRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.security.AuthenticatedUser;

/**
 * Kiểm tra một JWT (đã đúng chữ ký, chưa hết hạn) còn là phiên hợp lệ hay không và thu hồi
 * token khi đăng xuất (YCCN-03, 05).
 *
 * <p>Token hợp lệ khi: {@code jti} chưa bị thu hồi; người dùng tồn tại, đang ACTIVE; và
 * {@code ver} trong token khớp {@code users.token_version} (tăng khi khóa tài khoản, đổi quyền,
 * đổi/đặt lại mật khẩu). Nhờ vậy khóa tài khoản hay đổi quyền có hiệu lực ngay, không cần chờ
 * token hết hạn.
 */
@Service
public class SessionService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public SessionService(UserRepository userRepository, RevokedTokenRepository revokedTokenRepository) {
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    /** Trả danh tính nếu phiên còn hiệu lực, ngược lại rỗng. */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> validate(JwtClaims claims) {
        if (revokedTokenRepository.existsById(claims.jti())) {
            return Optional.empty();
        }
        Optional<User> user = userRepository.findById(claims.user().userId());
        if (user.isEmpty()) {
            return Optional.empty();
        }
        User account = user.get();
        boolean active = "ACTIVE".equalsIgnoreCase(account.getStatus());
        boolean sameVersion = account.getTokenVersion() == claims.version();
        // username trong token phải khớp tài khoản (phòng trường hợp ID được tái sử dụng)
        boolean sameUsername = account.getUsername().equals(claims.user().username());
        return active && sameVersion && sameUsername ? Optional.of(claims.user()) : Optional.empty();
    }

    /** Thu hồi token (đăng xuất). Gọi lặp lại là an toàn. */
    @Transactional
    public void revoke(JwtClaims claims) {
        if (revokedTokenRepository.existsById(claims.jti())) {
            return;
        }
        revokedTokenRepository.save(RevokedToken.builder()
                .jti(claims.jti())
                .userId(claims.user().userId())
                .expiresAt(LocalDateTime.ofInstant(claims.expiresAt(), ZONE))
                .revokedAt(LocalDateTime.now())
                .build());
    }

    /** Dọn các token đã thu hồi mà bản thân chúng cũng đã hết hạn. */
    @Scheduled(fixedDelayString = "${medbook.security.revoked-token-cleanup-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        revokedTokenRepository.deleteExpired(LocalDateTime.now());
    }
}
