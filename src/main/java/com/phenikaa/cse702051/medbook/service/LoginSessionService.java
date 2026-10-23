package com.phenikaa.cse702051.medbook.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.phenikaa.cse702051.medbook.config.JwtUtil;
import com.phenikaa.cse702051.medbook.dto.RefreshResponse;
import com.phenikaa.cse702051.medbook.model.AuthSession;
import com.phenikaa.cse702051.medbook.model.RefreshToken;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.*;
import com.phenikaa.cse702051.medbook.exception.UnauthorizedException;

@Service
public class LoginSessionService {
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final UserRoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final JwtUtil jwt;
    private final long lifetime;
    private final SecureRandom random = new SecureRandom();

    public LoginSessionService(AuthSessionRepository sessions, UserRepository users,
            UserRoleRepository roles, RefreshTokenRepository refreshTokens, JwtUtil jwt,
            @Value("${medbook.security.refresh-expiration-ms:604800000}") long lifetime) {
        if (lifetime <= 0) throw new IllegalArgumentException("Session lifetime must be positive");
        this.sessions = sessions;
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.lifetime = lifetime;
    }

    /** Used by API test fixtures; still creates a real persisted session. */
    @Transactional
    public JwtUtil.IssuedToken create(Long userId) {
        var issued = createTokens(userId);
        var claims = jwt.parseClaims(issued.token()).orElseThrow(this::invalid);
        return new JwtUtil.IssuedToken(issued.token(), claims.jti(), claims.expiresAt());
    }

    @Transactional
    public RefreshResponse createTokens(Long userId) {
        var user = users.findByIdForUpdate(userId).orElseThrow(this::invalid);
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) throw invalid();
        var session = new AuthSession();
        session.setId(UUID.randomUUID().toString());
        session.setUser(user);
        session.setTokenVersion(user.getTokenVersion());
        session.setCreatedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        session.setExpiresAt(session.getCreatedAt().plusMillis(lifetime));
        sessions.save(session);
        return issue(session, user);
    }

    /** User lock precedes session lock. Replay revocation must commit even though HTTP is 401. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshResponse refresh(String raw) {
        if (raw == null || !raw.matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}")) throw invalid();
        String sid = raw.substring(0, 36);
        var observed = sessions.findById(sid).orElseThrow(this::invalid);
        var user = users.findByIdForUpdate(observed.getUser().getId()).orElseThrow(this::invalid);
        var session = sessions.findLocked(sid).orElseThrow(this::invalid);
        var stored = refreshTokens.findById(hash(raw)).orElseThrow(this::invalid);
        if (!stored.getSession().getId().equals(sid)) throw invalid();
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(Instant.now())
                || !"ACTIVE".equalsIgnoreCase(user.getStatus())
                || session.getTokenVersion() != user.getTokenVersion()) throw invalid();
        if (stored.getUsedAt() != null) {
            session.setRevokedAt(Instant.now());
            sessions.saveAndFlush(session);
            throw invalid();
        }
        stored.setUsedAt(Instant.now());
        refreshTokens.save(stored);
        return issue(session, user);
    }

    private RefreshResponse issue(AuthSession session, User user) {
        var roleCodes = roles.findByUserIdWithRole(user.getId()).stream().map(r -> r.getRole().getCode()).toList();
        if (roleCodes.isEmpty()) throw invalid();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = session.getId() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var stored = new RefreshToken();
        stored.setTokenHash(hash(raw));
        stored.setSession(session);
        stored.setCreatedAt(Instant.now());
        refreshTokens.save(stored);
        var access = jwt.issueToken(user.getId(), user.getUsername(), roleCodes,
                session.getTokenVersion(), session.getId(), session.getExpiresAt());
        return new RefreshResponse(access.token(), "Bearer", access.expiresAt().toString(), raw,
                session.getExpiresAt().toString());
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
    private UnauthorizedException invalid() { return new UnauthorizedException("Phiên đăng nhập không còn hợp lệ"); }
}
