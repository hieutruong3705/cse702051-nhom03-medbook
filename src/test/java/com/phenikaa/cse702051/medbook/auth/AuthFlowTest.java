package com.phenikaa.cse702051.medbook.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.config.JwtUtil.JwtClaims;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.PasswordResetTokenRepository;
import com.phenikaa.cse702051.medbook.repository.RevokedTokenRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.repository.UserRoleRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.CapturingPasswordResetNotifier;

/**
 * BE-01 (YCCN-01…04): đăng ký, đăng nhập, khóa 5 lần/15 phút, đăng xuất thu hồi token, đổi mật khẩu,
 * quên/đặt lại mật khẩu, audit. Mỗi test dùng tài khoản riêng (tên duy nhất) để bộ đếm khóa không
 * ảnh hưởng lẫn nhau.
 */
class AuthFlowTest extends AbstractApiTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PASSWORD = "Sup3r#Secret";

    @Autowired
    private UserRepository users;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private RevokedTokenRepository revokedTokens;

    @Autowired
    private PasswordResetTokenRepository resetTokens;

    @Autowired
    private CapturingPasswordResetNotifier notifier;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- helpers ----------

    private String newName() {
        return "be01u" + COUNTER.incrementAndGet() + Long.toString(System.nanoTime() % 1_000_000, 36);
    }

    private static String emailOf(String username) {
        return username + "@example.com";
    }

    private static String registerBody(String username, String password) {
        return """
                {"username":"%s","password":"%s","fullName":"Nguyễn Văn Thử","email":"%s","phone":"0912345000"}
                """.formatted(username, password, emailOf(username));
    }

    private ResultActions postJson(String url, String body, String bearer) throws Exception {
        MockHttpServletRequestBuilder request = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (bearer != null) {
            request.header("Authorization", bearer);
        }
        return mvc.perform(request);
    }

    private ResultActions register(String username, String password) throws Exception {
        return postJson("/api/v1/auth/register", registerBody(username, password), null);
    }

    private ResultActions login(String identifier, String password) throws Exception {
        return postJson("/api/v1/auth/login",
                "{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password), null);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Đăng ký tài khoản mới rồi đăng nhập; trả về phản hồi đăng nhập. */
    private JsonNode registerAndLogin(String username) throws Exception {
        register(username, PASSWORD).andExpect(status().isCreated());
        return body(login(username, PASSWORD).andExpect(status().isOk()));
    }

    private static String bearerOf(JsonNode loginResponse) {
        return "Bearer " + loginResponse.get("token").asText();
    }

    private User reload(String username) {
        return users.findByUsername(username).orElseThrow();
    }

    private void assertAuthorized(String bearer, int expectedStatus) throws Exception {
        mvc.perform(get("/api/v1/patients/me").header("Authorization", bearer)).andExpect(status().is(expectedStatus));
    }

    private static String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private List<AuditLog> auditsByAction(String action, Long entityId) {
        return auditLogs.findByActionCode(action).stream()
                .filter(a -> entityId == null || entityId.equals(a.getEntityId()))
                .toList();
    }

    // ---------- đăng ký ----------

    @Test
    @DisplayName("Đăng ký: 201, chỉ role PATIENT (bỏ qua role/status do client gửi), không trả mật khẩu/hash, hash BCrypt cost ≥ 10")
    void registerCreatesPatientOnly() throws Exception {
        String username = newName();
        String payload = """
                {"username":"%s","password":"%s","fullName":"Kẻ Tấn Công","email":"%s","phone":"0912345000",
                 "roles":["ADMIN"],"role":"ADMIN","status":"LOCKED","tokenVersion":99,"passwordHash":"x"}
                """.formatted(username, PASSWORD, emailOf(username));

        MvcResult result = postJson("/api/v1/auth/register", payload, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.patientCode").exists())
                .andReturn();

        String responseText = result.getResponse().getContentAsString();
        assertFalse(responseText.toLowerCase().contains("password"), "phản hồi không được chứa mật khẩu/hash");

        User saved = reload(username);
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals(0, saved.getTokenVersion());
        assertEquals(List.of("PATIENT"), userRoles.findRoleCodesByUserId(saved.getId()));
        assertTrue(saved.getPasswordHash().startsWith("$2"), "phải là BCrypt");
        int cost = Integer.parseInt(saved.getPasswordHash().split("\\$")[2]);
        assertTrue(cost >= 10, "BCrypt cost phải ≥ 10, nhận " + cost);
        assertNotEquals(PASSWORD, saved.getPasswordHash());
    }

    @ParameterizedTest(name = "mật khẩu \"{0}\" bị từ chối")
    @ValueSource(strings = { "short1", "onlyletters", "123456789", "        ", "Ab1" })
    @DisplayName("Đăng ký: mật khẩu yếu → 400 VALIDATION_FAILED có lỗi ở trường password")
    void registerRejectsWeakPasswords(String weak) throws Exception {
        register(newName(), weak)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    @DisplayName("Đăng ký: trùng tên đăng nhập hoặc email (không phân biệt hoa thường) → 409")
    void registerRejectsDuplicates() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());

        register(username, PASSWORD).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));

        String other = newName();
        String sameEmailUpper = """
                {"username":"%s","password":"%s","fullName":"Khác","email":"%s","phone":"0912345001"}
                """.formatted(other, PASSWORD, emailOf(username).toUpperCase());
        postJson("/api/v1/auth/register", sameEmailUpper, null).andExpect(status().isConflict());
        assertFalse(users.existsByUsername(other));
    }

    // ---------- đăng nhập ----------

    @Test
    @DisplayName("Đăng nhập đúng bằng username hoặc email: 200, có token, hạn, roles, patientId; không lộ hash")
    void loginSucceeds() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());

        JsonNode byUsername = body(login(username, PASSWORD).andExpect(status().isOk()));
        JsonNode byEmail = body(login(emailOf(username), PASSWORD).andExpect(status().isOk()));

        for (JsonNode response : List.of(byUsername, byEmail)) {
            assertEquals("Bearer", response.get("tokenType").asText());
            assertEquals(username, response.get("username").asText());
            assertEquals("PATIENT", response.get("roles").get(0).asText());
            assertTrue(response.get("patientId").asLong() > 0);
            assertTrue(response.get("doctorId").isNull());
            assertTrue(Instant.parse(response.get("expiresAt").asText()).isAfter(Instant.now()));
            assertFalse(response.toString().toLowerCase().contains("hash"));
        }
        assertNotEquals(byUsername.get("token").asText(), byEmail.get("token").asText());
        assertAuthorized(bearerOf(byUsername), 200);
    }

    @Test
    @DisplayName("Sai mật khẩu và tài khoản không tồn tại cho cùng một phản hồi 401 (không lộ tài khoản)")
    void wrongPasswordAndUnknownUserLookIdentical() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());

        JsonNode wrong = body(login(username, "Sai#Mat1khau").andExpect(status().isUnauthorized()));
        JsonNode unknown = body(login("khong_ton_tai_" + username, "Sai#Mat1khau").andExpect(status().isUnauthorized()));

        assertEquals(wrong.get("code").asText(), unknown.get("code").asText());
        assertEquals(wrong.get("message").asText(), unknown.get("message").asText());
    }

    @Test
    @DisplayName("Sai 5 lần liên tiếp → khóa 15 phút (403 ACCOUNT_LOCKED + lockedUntil); đúng mật khẩu cũng bị từ chối")
    void accountLocksAfterFiveFailures() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());

        for (int i = 1; i <= 4; i++) {
            login(username, "Sai#Mat1khau").andExpect(status().isUnauthorized());
            assertEquals(i, reload(username).getFailedLoginCount());
        }
        LocalDateTime beforeFifth = LocalDateTime.now();
        login(username, "Sai#Mat1khau")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.details.lockedUntil").exists());

        User locked = reload(username);
        assertNotNull(locked.getLockedUntil());
        long minutes = Duration.between(beforeFifth, locked.getLockedUntil()).toMinutes();
        assertTrue(minutes >= 14 && minutes <= 15, "khóa phải kéo dài ≈ 15 phút, nhận " + minutes);

        // đang khóa: ngay cả mật khẩu ĐÚNG cũng bị từ chối và không gia hạn khóa
        LocalDateTime lockedUntil = locked.getLockedUntil();
        login(username, PASSWORD).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        assertEquals(lockedUntil, reload(username).getLockedUntil());

        // audit: 5 lần LOGIN_FAILED và 1 ACCOUNT_LOCKED cho tài khoản này
        Long id = locked.getId();
        assertTrue(auditsByAction(AuditActions.LOGIN_FAILED, id).size() >= 5);
        assertEquals(1, auditsByAction(AuditActions.ACCOUNT_LOCKED, id).size());
    }

    @Test
    @DisplayName("Hết 15 phút khóa → đăng nhập lại được và bộ đếm được đặt lại")
    void lockExpires() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            login(username, "Sai#Mat1khau");
        }
        login(username, PASSWORD).andExpect(status().isForbidden());

        // mô phỏng 15 phút trôi qua: đưa thời điểm mở khóa về quá khứ
        User user = reload(username);
        user.setLockedUntil(LocalDateTime.now().minusSeconds(1));
        users.save(user);

        login(username, PASSWORD).andExpect(status().isOk());
        User after = reload(username);
        assertEquals(0, after.getFailedLoginCount());
        assertEquals(null, after.getLockedUntil());
    }

    @Test
    @DisplayName("Đăng nhập đúng xóa bộ đếm sai; cần đủ 5 lần sai mới khóa lại")
    void successResetsFailureCounter() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());

        for (int i = 0; i < 4; i++) {
            login(username, "Sai#Mat1khau").andExpect(status().isUnauthorized());
        }
        login(username, PASSWORD).andExpect(status().isOk());
        assertEquals(0, reload(username).getFailedLoginCount());

        for (int i = 0; i < 4; i++) {
            login(username, "Sai#Mat1khau").andExpect(status().isUnauthorized());
        }
        login(username, "Sai#Mat1khau").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tài khoản bị Admin khóa: đúng mật khẩu → 403 ACCOUNT_LOCKED (không lockedUntil); sai mật khẩu → 401 (không lộ)")
    void adminLockedAccountCannotLogin() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());
        User user = reload(username);
        user.setStatus("LOCKED");
        users.save(user);

        login(username, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.details.lockedUntil").doesNotExist());
        login(username, "Sai#Mat1khau").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tài khoản chưa có vai trò nào không đăng nhập được (403), thay vì mặc định PATIENT")
    void accountWithoutRolesIsRejected() throws Exception {
        String username = "be01norole" + COUNTER.incrementAndGet();
        data.user(username, PASSWORD); // không gán role

        login(username, PASSWORD).andExpect(status().isForbidden());
    }

    // ---------- phiên / token ----------

    @Test
    @DisplayName("Mỗi lần đăng nhập cấp token có jti riêng và ver = token_version của tài khoản")
    void tokensCarryJtiAndVersion() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());

        JwtClaims first = jwtUtil.parseClaims(body(login(username, PASSWORD)).get("token").asText()).orElseThrow();
        JwtClaims second = jwtUtil.parseClaims(body(login(username, PASSWORD)).get("token").asText()).orElseThrow();

        assertNotEquals(first.jti(), second.jti());
        assertEquals(reload(username).getTokenVersion(), first.version());
        assertEquals(username, first.user().username());
    }

    @Test
    @DisplayName("Đăng xuất: 204, token bị thu hồi (dùng lại → 401); token khác của cùng người vẫn dùng được")
    void logoutRevokesOnlyThatToken() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());
        String tokenA = bearerOf(body(login(username, PASSWORD)));
        String tokenB = bearerOf(body(login(username, PASSWORD)));
        assertAuthorized(tokenA, 200);

        String jtiA = jwtUtil.parseClaims(tokenA.substring("Bearer ".length())).orElseThrow().jti();
        String jtiB = jwtUtil.parseClaims(tokenB.substring("Bearer ".length())).orElseThrow().jti();

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", tokenA)).andExpect(status().isNoContent());

        assertAuthorized(tokenA, 401);
        assertAuthorized(tokenB, 200);
        assertTrue(revokedTokens.existsById(jtiA), "jti của token đăng xuất phải được ghi vào danh sách thu hồi");
        assertFalse(revokedTokens.existsById(jtiB));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", tokenA)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Đăng xuất không có token → 401")
    void logoutRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer rac.rac.rac"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tài khoản bị khóa sau khi đăng nhập → token cũ bị 401 ngay (kể cả khi chưa tăng token_version)")
    void lockedAccountTokenIsRejected() throws Exception {
        String username = newName();
        String token = bearerOf(registerAndLogin(username));
        assertAuthorized(token, 200);

        User user = reload(username);
        user.setStatus("LOCKED");
        users.save(user);

        assertAuthorized(token, 401);
    }

    @Test
    @DisplayName("Token mang ver cũ (đã có thay đổi phiên bản) bị 401; token cấp sau đó hợp lệ")
    void staleTokenVersionIsRejected() throws Exception {
        String username = newName();
        String oldToken = bearerOf(registerAndLogin(username));

        User user = reload(username);
        user.setTokenVersion(user.getTokenVersion() + 1);
        users.save(user);

        assertAuthorized(oldToken, 401);
        assertAuthorized(bearerOf(body(login(username, PASSWORD))), 200);
    }

    @Test
    @DisplayName("Token của tài khoản đã bị xóa hoặc đổi username bị 401")
    void tokenOfMissingUserIsRejected() throws Exception {
        String token = bearer(jwtUtil.generateToken(987654321L, "khong_ton_tai", List.of("PATIENT")));
        assertAuthorized(token, 401);
    }

    // ---------- đổi mật khẩu ----------

    @Test
    @DisplayName("Đổi mật khẩu: mật khẩu cũ sai → 400 (không phải 401) kèm details.oldPassword")
    void changePasswordWithWrongOldPassword() throws Exception {
        String token = bearerOf(registerAndLogin(newName()));

        postJson("/api/v1/auth/change-password",
                "{\"oldPassword\":\"Sai#Mat1khau\",\"newPassword\":\"Moi#Mat2khau\"}", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.oldPassword").exists());
        assertAuthorized(token, 200); // vẫn đăng nhập, không bị đá ra
    }

    @Test
    @DisplayName("Đổi mật khẩu: mật khẩu mới trùng cũ hoặc yếu → 400")
    void changePasswordValidatesNewPassword() throws Exception {
        String token = bearerOf(registerAndLogin(newName()));

        postJson("/api/v1/auth/change-password",
                "{\"oldPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(PASSWORD, PASSWORD), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword").exists());
        postJson("/api/v1/auth/change-password",
                "{\"oldPassword\":\"%s\",\"newPassword\":\"yeuqua\"}".formatted(PASSWORD), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword").exists());
    }

    @Test
    @DisplayName("Đổi mật khẩu thành công: 204, token cũ bị vô hiệu, mật khẩu cũ hết dùng, mật khẩu mới dùng được, có audit")
    void changePasswordInvalidatesOldSessions() throws Exception {
        String username = newName();
        String token = bearerOf(registerAndLogin(username));
        String otherSession = bearerOf(body(login(username, PASSWORD)));
        long auditsBefore = auditsByAction(AuditActions.PASSWORD_CHANGED, reload(username).getId()).size();

        postJson("/api/v1/auth/change-password",
                "{\"oldPassword\":\"%s\",\"newPassword\":\"Moi#Mat2khau\"}".formatted(PASSWORD), token)
                .andExpect(status().isNoContent());

        assertAuthorized(token, 401);
        assertAuthorized(otherSession, 401);
        login(username, PASSWORD).andExpect(status().isUnauthorized());
        JsonNode fresh = body(login(username, "Moi#Mat2khau").andExpect(status().isOk()));
        assertAuthorized(bearerOf(fresh), 200);
        assertEquals(auditsBefore + 1, auditsByAction(AuditActions.PASSWORD_CHANGED, reload(username).getId()).size());
    }

    @Test
    @DisplayName("Đổi mật khẩu yêu cầu đã đăng nhập → 401")
    void changePasswordRequiresAuthentication() throws Exception {
        postJson("/api/v1/auth/change-password",
                "{\"oldPassword\":\"a\",\"newPassword\":\"Moi#Mat2khau\"}", null)
                .andExpect(status().isUnauthorized());
    }

    // ---------- quên / đặt lại mật khẩu ----------

    @Test
    @DisplayName("Quên mật khẩu: luôn 202 cùng một nội dung dù email có tồn tại hay không; chỉ email thật mới có token")
    void forgotPasswordDoesNotRevealAccounts() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());

        JsonNode known = body(postJson("/api/v1/auth/forgot-password",
                "{\"email\":\"%s\"}".formatted(emailOf(username)), null).andExpect(status().isAccepted()));
        JsonNode unknown = body(postJson("/api/v1/auth/forgot-password",
                "{\"email\":\"khong.co.%s\"}".formatted(emailOf(username)), null).andExpect(status().isAccepted()));

        assertEquals(known.toString(), unknown.toString());
        assertTrue(notifier.lastTokenFor(emailOf(username)).isPresent());
        assertTrue(notifier.lastTokenFor("khong.co." + emailOf(username)).isEmpty());
    }

    @Test
    @DisplayName("Quên mật khẩu: email sai định dạng → 400")
    void forgotPasswordValidatesEmail() throws Exception {
        postJson("/api/v1/auth/forgot-password", "{\"email\":\"khong-phai-email\"}", null)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Token đặt lại: chỉ lưu băm SHA-256 (không lưu token gốc), hạn ≈ 15 phút")
    void resetTokenIsStoredHashedWithShortExpiry() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());
        LocalDateTime before = LocalDateTime.now();

        postJson("/api/v1/auth/forgot-password", "{\"email\":\"%s\"}".formatted(emailOf(username)), null)
                .andExpect(status().isAccepted());

        String raw = notifier.lastTokenFor(emailOf(username)).orElseThrow();
        assertTrue(raw.length() >= 40, "token phải đủ dài (256 bit)");
        assertTrue(resetTokens.findByTokenHash(sha256(raw)).isPresent());
        assertTrue(resetTokens.findByTokenHash(raw).isEmpty(), "CSDL không được chứa token gốc");
        long minutes = Duration.between(before, notifier.lastExpiryFor(emailOf(username)).orElseThrow()).toMinutes();
        assertTrue(minutes >= 14 && minutes <= 15, "hạn token ≈ 15 phút, nhận " + minutes);
    }

    @Test
    @DisplayName("Đặt lại mật khẩu đúng: 204, dùng một lần, vô hiệu phiên cũ, mở khóa đăng nhập sai, đăng nhập được bằng mật khẩu mới")
    void resetPasswordHappyPath() throws Exception {
        String username = newName();
        String oldSession = bearerOf(registerAndLogin(username));
        for (int i = 0; i < 5; i++) {
            login(username, "Sai#Mat1khau"); // đang bị khóa tạm
        }
        postJson("/api/v1/auth/forgot-password", "{\"email\":\"%s\"}".formatted(emailOf(username)), null);
        String raw = notifier.lastTokenFor(emailOf(username)).orElseThrow();

        postJson("/api/v1/auth/reset-password", "{\"token\":\"%s\",\"newPassword\":\"Moi#Mat2khau\"}".formatted(raw), null)
                .andExpect(status().isNoContent());

        // token chỉ dùng một lần
        postJson("/api/v1/auth/reset-password", "{\"token\":\"%s\",\"newPassword\":\"Khac#Mat3khau\"}".formatted(raw), null)
                .andExpect(status().isBadRequest());
        assertAuthorized(oldSession, 401);
        login(username, PASSWORD).andExpect(status().isUnauthorized());
        JsonNode fresh = body(login(username, "Moi#Mat2khau").andExpect(status().isOk()));
        assertAuthorized(bearerOf(fresh), 200);
        assertEquals(null, reload(username).getLockedUntil());
        assertEquals(1, auditsByAction(AuditActions.PASSWORD_RESET, reload(username).getId()).size());
    }

    @Test
    @DisplayName("Đặt lại mật khẩu: token sai, hết hạn hoặc đã bị thay bằng token mới đều → cùng một lỗi 400")
    void resetPasswordRejectsBadTokens() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());
        String newPassword = "Moi#Mat2khau";

        JsonNode wrong = body(postJson("/api/v1/auth/reset-password",
                "{\"token\":\"khong-dung\",\"newPassword\":\"%s\"}".formatted(newPassword), null)
                .andExpect(status().isBadRequest()));

        // hết hạn
        postJson("/api/v1/auth/forgot-password", "{\"email\":\"%s\"}".formatted(emailOf(username)), null);
        String expiredRaw = notifier.lastTokenFor(emailOf(username)).orElseThrow();
        var entity = resetTokens.findByTokenHash(sha256(expiredRaw)).orElseThrow();
        entity.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        resetTokens.save(entity);
        JsonNode expired = body(postJson("/api/v1/auth/reset-password",
                "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(expiredRaw, newPassword), null)
                .andExpect(status().isBadRequest()));

        // yêu cầu lại: token trước đó bị vô hiệu
        postJson("/api/v1/auth/forgot-password", "{\"email\":\"%s\"}".formatted(emailOf(username)), null);
        String first = notifier.lastTokenFor(emailOf(username)).orElseThrow();
        postJson("/api/v1/auth/forgot-password", "{\"email\":\"%s\"}".formatted(emailOf(username)), null);
        String second = notifier.lastTokenFor(emailOf(username)).orElseThrow();
        assertNotEquals(first, second);
        JsonNode superseded = body(postJson("/api/v1/auth/reset-password",
                "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(first, newPassword), null)
                .andExpect(status().isBadRequest()));

        assertEquals(wrong.get("message").asText(), expired.get("message").asText());
        assertEquals(wrong.get("message").asText(), superseded.get("message").asText());
        // token mới nhất vẫn dùng được
        postJson("/api/v1/auth/reset-password",
                "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(second, newPassword), null)
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Đặt lại mật khẩu: mật khẩu mới yếu → 400 và KHÔNG làm mất token")
    void weakResetPasswordKeepsTokenUsable() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());
        postJson("/api/v1/auth/forgot-password", "{\"email\":\"%s\"}".formatted(emailOf(username)), null);
        String raw = notifier.lastTokenFor(emailOf(username)).orElseThrow();

        postJson("/api/v1/auth/reset-password", "{\"token\":\"%s\",\"newPassword\":\"yeu\"}".formatted(raw), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword").exists());

        postJson("/api/v1/auth/reset-password", "{\"token\":\"%s\",\"newPassword\":\"Moi#Mat2khau\"}".formatted(raw), null)
                .andExpect(status().isNoContent());
    }

    // ---------- audit đăng nhập ----------

    @Test
    @DisplayName("Audit: LOGIN_SUCCESS ghi đúng người, IP và không chứa mật khẩu; LOGIN_FAILED ghi username + lý do, không ghi mật khẩu")
    void loginEventsAreAudited() throws Exception {
        String username = newName();
        register(username, PASSWORD).andExpect(status().isCreated());
        Long id = reload(username).getId();

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"%s\",\"password\":\"Sai#Mat1khau\"}".formatted(username))
                .with(r -> {
                    r.setRemoteAddr("172.16.0.77");
                    return r;
                }));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD))
                .with(r -> {
                    r.setRemoteAddr("172.16.0.78");
                    return r;
                })).andExpect(status().isOk());

        AuditLog failed = auditsByAction(AuditActions.LOGIN_FAILED, id).getLast();
        assertEquals(null, failed.getActorUser(), "người đăng nhập thất bại chưa xác thực nên không có actor");
        assertEquals("172.16.0.77", failed.getIpAddress());
        Map<?, ?> failedMeta = JSON.readValue(failed.getMetadataJson(), Map.class);
        assertEquals(username, failedMeta.get("username"));
        assertEquals("WRONG_PASSWORD", failedMeta.get("reason"));

        AuditLog success = auditsByAction(AuditActions.LOGIN_SUCCESS, id).getLast();
        assertEquals(id, success.getActorUser().getId());
        assertEquals("172.16.0.78", success.getIpAddress());
        for (AuditLog log : List.of(failed, success)) {
            assertFalse(log.getMetadataJson().contains("Sai#Mat1khau") || log.getMetadataJson().contains(PASSWORD),
                    "audit không được chứa mật khẩu");
        }
    }
}
