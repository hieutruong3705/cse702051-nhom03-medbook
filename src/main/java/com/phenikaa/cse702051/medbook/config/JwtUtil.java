package com.phenikaa.cse702051.medbook.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.phenikaa.cse702051.medbook.security.AuthenticatedUser;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Phát hành và xác minh JWT (HS256).
 *
 * <p>Claim: {@code sub}=username, {@code userId}, {@code roles}, {@code ver} (phiên bản token
 * của người dùng), {@code jti} (mã định danh token, dùng để thu hồi khi đăng xuất), {@code iat},
 * {@code exp}. Việc token còn hiệu lực về mặt nghiệp vụ (chưa thu hồi, tài khoản còn hoạt động,
 * {@code ver} khớp) do {@code SessionService} kiểm tra.
 */
@Component
public class JwtUtil {

    /** Kết quả phân tích một token đã đúng chữ ký và chưa hết hạn. */
    public record JwtClaims(AuthenticatedUser user, String jti, int version, Instant expiresAt) {
    }

    /** Token vừa phát hành kèm thông tin phục vụ phản hồi đăng nhập. */
    public record IssuedToken(String token, String jti, Instant expiresAt) {
    }

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Phát hành token cho phiên bản token {@code version} của người dùng. */
    public IssuedToken issueToken(Long userId, String username, List<String> roles, int version) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .id(jti)
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .claim("ver", version)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, expiry.toInstant());
    }

    /** Tạo token với phiên bản 0 (giữ tương thích mã cũ và test). */
    public String generateToken(Long userId, String username, List<String> roles) {
        return issueToken(userId, username, roles, 0).token();
    }

    /**
     * Xác minh chữ ký + hạn và trích mọi claim cần thiết trong một lần phân tích.
     * Trả rỗng nếu token sai, hết hạn hoặc thiếu claim bắt buộc.
     */
    public Optional<JwtClaims> parseClaims(String token) {
        try {
            Claims claims = getClaims(token);
            Number userId = claims.get("userId", Number.class);
            String username = claims.getSubject();
            Set<String> roles = readRoles(claims.get("roles"));
            if (userId == null || username == null || username.isBlank() || roles.isEmpty()
                    || claims.getId() == null || claims.getExpiration() == null) {
                return Optional.empty();
            }
            Number version = claims.get("ver", Number.class);
            return Optional.of(new JwtClaims(
                    new AuthenticatedUser(userId.longValue(), username, roles),
                    claims.getId(),
                    version == null ? 0 : version.intValue(),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException | ClassCastException e) {
            return Optional.empty();
        }
    }

    /** Chỉ lấy danh tính (không xét thu hồi/phiên bản). */
    public Optional<AuthenticatedUser> parseAuthenticatedUser(String token) {
        return parseClaims(token).map(JwtClaims::user);
    }

    /**
     * Trích xuất username từ token
     */
    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Trích xuất userId từ token
     */
    public Long getUserId(String token) {
        Number userId = getClaims(token).get("userId", Number.class);
        return userId == null ? null : userId.longValue();
    }

    /**
     * Trích xuất danh sách roles từ token
     */
    public List<String> getRoles(String token) {
        return List.copyOf(readRoles(getClaims(token).get("roles")));
    }

    /**
     * Kiểm tra token hợp lệ (chữ ký đúng + chưa hết hạn)
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Set<String> readRoles(Object claim) {
        Set<String> roles = new HashSet<>();
        if (claim instanceof Collection<?> values) {
            for (Object value : values) {
                if (value instanceof String role && !role.isBlank()) {
                    roles.add(role);
                }
            }
        }
        return roles;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
