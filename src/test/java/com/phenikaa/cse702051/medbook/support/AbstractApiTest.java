package com.phenikaa.cse702051.medbook.support;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.phenikaa.cse702051.medbook.config.JwtUtil;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.repository.AuditLogRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Nền chung cho test API: một Spring context + MockMvc dùng chung, cùng bộ
 * token mẫu khớp với dữ liệu seed trong {@code data.sql}
 * (admin1=1, doctor1=2, doctor2=3, patient1=4, patient2=5).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractApiTest {

    public static final long ADMIN_USER_ID = 1L;
    public static final long DOCTOR1_USER_ID = 2L;
    public static final long DOCTOR2_USER_ID = 3L;
    public static final long PATIENT1_USER_ID = 4L;
    public static final long PATIENT2_USER_ID = 5L;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JwtUtil jwtUtil;

    @Autowired
    protected com.phenikaa.cse702051.medbook.service.LoginSessionService loginSessions;

    @Autowired
    protected ApiTestData data;

    @Autowired
    protected AuditLogRepository auditLogs;

    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Các bản ghi audit của một đối tượng, cũ nhất trước. */
    protected List<AuditLog> auditsOf(String entityType, Long entityId) {
        return auditLogs.findByEntityTypeAndEntityId(entityType, entityId).stream()
                .sorted(Comparator.comparing(AuditLog::getId))
                .toList();
    }

    /** Số bản ghi audit của đối tượng với mã hành động và người thực hiện cho trước. */
    protected long countAudits(String entityType, Long entityId, String actionCode, Long actorUserId) {
        return auditsOf(entityType, entityId).stream()
                .filter(a -> a.getActionCode().equals(actionCode))
                .filter(a -> actorUserId == null
                        ? a.getActorUser() == null
                        : a.getActorUser() != null && a.getActorUser().getId().equals(actorUserId))
                .count();
    }

    protected String adminToken() {
        return bearer(loginSessions.create(ADMIN_USER_ID).token());
    }

    protected String doctorToken() {
        return bearer(loginSessions.create(DOCTOR1_USER_ID).token());
    }

    protected String doctor2Token() {
        return bearer(loginSessions.create(DOCTOR2_USER_ID).token());
    }

    protected String patientToken() {
        return bearer(loginSessions.create(PATIENT1_USER_ID).token());
    }

    protected String patient2Token() {
        return bearer(loginSessions.create(PATIENT2_USER_ID).token());
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Token có chữ ký đúng nhưng đã hết hạn. */
    protected String expiredToken(long userId, String username, String role) {
        Date issued = new Date(System.currentTimeMillis() - 2 * 3600_000L);
        Date expired = new Date(System.currentTimeMillis() - 3600_000L);
        return bearer(Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", List.of(role))
                .issuedAt(issued)
                .expiration(expired)
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact());
    }

    /** Token ký bằng khóa khác (giả mạo). */
    protected String forgedToken(long userId, String username, String role) {
        String otherSecret = "AnotherSecretKeyThatIsAtLeastThirtyTwoBytesLong-0123456789";
        return bearer(Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", List.of(role))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(Keys.hmacShaKeyFor(otherSecret.getBytes(StandardCharsets.UTF_8)))
                .compact());
    }

    /** Token hợp lệ về chữ ký nhưng thiếu claim bắt buộc (roles rỗng). */
    protected String tokenWithoutRoles(long userId, String username) {
        return bearer(Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", List.of())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact());
    }
}
