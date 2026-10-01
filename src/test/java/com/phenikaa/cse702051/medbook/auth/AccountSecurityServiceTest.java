package com.phenikaa.cse702051.medbook.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.repository.UserRoleRepository;
import com.phenikaa.cse702051.medbook.security.AuthenticatedUser;
import com.phenikaa.cse702051.medbook.service.AccountSecurityService;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * BE-01 (YCCN-05, 06): khóa/mở tài khoản và gán quyền — vô hiệu hóa token ngay, chống tự khóa, chống
 * làm mất quản trị viên cuối cùng, ghi audit kèm trước/sau.
 */
class AccountSecurityServiceTest extends AbstractApiTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final String PASSWORD = "Sup3r#Secret";

    @Autowired
    private AccountSecurityService accountSecurity;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserRoleRepository userRoles;

    private String name(String prefix) {
        return prefix + COUNTER.incrementAndGet() + Long.toString(System.nanoTime() % 1_000_000, 36);
    }

    private void actAs(User actor, String... roles) {
        AuthenticatedUser principal = new AuthenticatedUser(actor.getId(), actor.getUsername(), Set.of(roles));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    private User admin() {
        return data.user(name("be01adm"), PASSWORD, "ADMIN");
    }

    private User patientAccount() {
        return data.user(name("be01pat"), PASSWORD, "PATIENT");
    }

    private String tokenOf(User user, String role) {
        User fresh = users.findById(user.getId()).orElseThrow();
        return bearer(loginSessions.create(fresh.getId()).token());
    }

    private void assertToken(String token, int expected) throws Exception {
        mvc.perform(get("/api/v1/patients/me").header("Authorization", token)).andExpect(status().is(expected));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------- khóa / mở ----------

    @Test
    @DisplayName("Khóa tài khoản: token đã cấp bị 401 ngay, token_version tăng, có audit trước/sau kèm lý do")
    void lockInvalidatesExistingTokens() throws Exception {
        User admin = admin();
        User target = patientAccount();
        String token = tokenOf(target, "PATIENT");
        assertToken(token, 403); // đã xác thực (không phải 401) nhưng tài khoản thử nghiệm không có hồ sơ bệnh nhân
        int versionBefore = users.findById(target.getId()).orElseThrow().getTokenVersion();

        actAs(admin, "ADMIN");
        User locked = accountSecurity.changeStatus(target.getId(), "locked", "Vi phạm điều khoản");

        assertEquals("LOCKED", locked.getStatus());
        assertEquals(versionBefore + 1, users.findById(target.getId()).orElseThrow().getTokenVersion());
        assertToken(token, 401);

        AuditLog log = auditsOf(AuditActions.ENTITY_USERS, target.getId()).stream()
                .filter(a -> a.getActionCode().equals(AuditActions.ACCOUNT_STATUS_CHANGED)).findFirst().orElseThrow();
        assertEquals(admin.getId(), log.getActorUser().getId());
        Map<?, ?> meta = new ObjectMapper().readValue(log.getMetadataJson(), Map.class);
        assertEquals("ACTIVE", meta.get("from"));
        assertEquals("LOCKED", meta.get("to"));
        assertEquals("Vi phạm điều khoản", meta.get("reason"));
    }

    @Test
    @DisplayName("Mở khóa: đăng nhập lại được, bộ đếm sai và khóa tạm được xóa")
    void unlockRestoresAccount() {
        User admin = admin();
        User target = patientAccount();
        actAs(admin, "ADMIN");
        accountSecurity.changeStatus(target.getId(), "LOCKED", "thử");
        User locked = users.findById(target.getId()).orElseThrow();
        locked.setFailedLoginCount(3);
        locked.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        users.save(locked);

        User unlocked = accountSecurity.changeStatus(target.getId(), "ACTIVE", null);

        assertEquals("ACTIVE", unlocked.getStatus());
        User reloaded = users.findById(target.getId()).orElseThrow();
        assertEquals(0, reloaded.getFailedLoginCount());
        assertEquals(null, reloaded.getLockedUntil());
    }

    @Test
    @DisplayName("Đặt cùng trạng thái hiện tại: không đổi gì, không tăng token_version")
    void sameStatusIsNoOp() {
        User admin = admin();
        User target = patientAccount();
        actAs(admin, "ADMIN");
        int version = users.findById(target.getId()).orElseThrow().getTokenVersion();

        accountSecurity.changeStatus(target.getId(), "ACTIVE", null);

        assertEquals(version, users.findById(target.getId()).orElseThrow().getTokenVersion());
    }

    @Test
    @DisplayName("Không thể tự khóa tài khoản của chính mình → 409")
    void cannotLockSelf() {
        User admin = admin();
        actAs(admin, "ADMIN");

        assertThrows(ConflictException.class, () -> accountSecurity.changeStatus(admin.getId(), "LOCKED", "x"));
        assertEquals("ACTIVE", users.findById(admin.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Không thể khóa quản trị viên đang hoạt động cuối cùng → 409 (giao dịch hoàn tác sau test)")
    @Transactional
    void cannotLockLastActiveAdmin() {
        User actor = admin();
        User target = admin();
        // khóa mọi admin khác trong giao dịch của test để `target` là admin hoạt động duy nhất còn lại
        userRepositoryAdmins().forEach(u -> {
            if (!u.getId().equals(target.getId()) && !u.getId().equals(actor.getId())) {
                u.setStatus("LOCKED");
                users.save(u);
            }
        });
        actor.setStatus("LOCKED"); // actor đứng ngoài: chỉ target còn hoạt động
        users.save(actor);
        actAs(actor, "ADMIN");

        assertThrows(ConflictException.class, () -> accountSecurity.changeStatus(target.getId(), "LOCKED", "x"));
    }

    @Test
    @DisplayName("Trạng thái không hợp lệ → 400; người dùng không tồn tại → 404; không phải ADMIN → 403")
    void statusValidationAndAuthorization() {
        User admin = admin();
        User target = patientAccount();
        actAs(admin, "ADMIN");

        assertThrows(FieldValidationException.class, () -> accountSecurity.changeStatus(target.getId(), "DISABLED", null));
        assertThrows(FieldValidationException.class, () -> accountSecurity.changeStatus(target.getId(), null, null));
        assertThrows(ResourceNotFoundException.class, () -> accountSecurity.changeStatus(987654321L, "LOCKED", null));

        actAs(target, "PATIENT");
        assertThrows(ForbiddenException.class, () -> accountSecurity.changeStatus(target.getId(), "LOCKED", null));
        assertThrows(ForbiddenException.class, () -> accountSecurity.changeRoles(target.getId(), List.of("ADMIN")));
        assertEquals(List.of("PATIENT"), userRoles.findRoleCodesByUserId(target.getId()));
    }

    // ---------- gán quyền ----------

    @Test
    @DisplayName("Đổi quyền: thay đúng tập quyền, token cũ (mang quyền cũ) bị 401, có audit before/after")
    void changeRolesReplacesRolesAndInvalidatesTokens() throws Exception {
        User admin = admin();
        User target = patientAccount();
        String oldToken = tokenOf(target, "PATIENT");
        assertToken(oldToken, 403); // đã xác thực (không phải 401) nhưng tài khoản thử nghiệm không có hồ sơ bệnh nhân

        actAs(admin, "ADMIN");
        accountSecurity.changeRoles(target.getId(), List.of("role_doctor", "DOCTOR", "PATIENT"));

        assertEquals(Set.of("DOCTOR", "PATIENT"), Set.copyOf(userRoles.findRoleCodesByUserId(target.getId())));
        assertToken(oldToken, 401);

        AuditLog log = auditsOf(AuditActions.ENTITY_USERS, target.getId()).stream()
                .filter(a -> a.getActionCode().equals(AuditActions.ROLE_CHANGED)).findFirst().orElseThrow();
        assertEquals(admin.getId(), log.getActorUser().getId());
        Map<?, ?> meta = new ObjectMapper().readValue(log.getMetadataJson(), Map.class);
        assertEquals(List.of("PATIENT"), meta.get("before"));
        assertEquals(Set.of("DOCTOR", "PATIENT"), Set.copyOf((List<?>) meta.get("after")));

        // gỡ bớt quyền (MockMvc đã xóa SecurityContext sau request ở trên nên đặt lại danh tính admin)
        actAs(admin, "ADMIN");
        accountSecurity.changeRoles(target.getId(), List.of("PATIENT"));
        assertEquals(List.of("PATIENT"), userRoles.findRoleCodesByUserId(target.getId()));
    }

    @Test
    @DisplayName("Đổi quyền: danh sách rỗng hoặc mã vai trò không tồn tại → 400, dữ liệu không đổi")
    void changeRolesValidatesInput() {
        User admin = admin();
        User target = patientAccount();
        actAs(admin, "ADMIN");
        int version = users.findById(target.getId()).orElseThrow().getTokenVersion();

        assertThrows(FieldValidationException.class, () -> accountSecurity.changeRoles(target.getId(), List.of()));
        assertThrows(FieldValidationException.class, () -> accountSecurity.changeRoles(target.getId(), null));
        assertThrows(FieldValidationException.class,
                () -> accountSecurity.changeRoles(target.getId(), List.of("PATIENT", "SUPERUSER")));

        assertEquals(List.of("PATIENT"), userRoles.findRoleCodesByUserId(target.getId()));
        assertEquals(version, users.findById(target.getId()).orElseThrow().getTokenVersion());
    }

    @Test
    @DisplayName("Không thể tự gỡ quyền ADMIN của chính mình → 409")
    void cannotRemoveOwnAdminRole() {
        User admin = admin();
        actAs(admin, "ADMIN");

        assertThrows(ConflictException.class, () -> accountSecurity.changeRoles(admin.getId(), List.of("PATIENT")));
        assertTrue(userRoles.findRoleCodesByUserId(admin.getId()).contains("ADMIN"));
    }

    @Test
    @DisplayName("Không thể gỡ quyền của quản trị viên đang hoạt động cuối cùng → 409")
    @Transactional
    void cannotRemoveLastAdminRole() {
        User actor = admin();
        User target = admin();
        userRepositoryAdmins().forEach(u -> {
            if (!u.getId().equals(target.getId()) && !u.getId().equals(actor.getId())) {
                u.setStatus("LOCKED");
                users.save(u);
            }
        });
        actor.setStatus("LOCKED");
        users.save(actor);
        actAs(actor, "ADMIN");

        assertThrows(ConflictException.class, () -> accountSecurity.changeRoles(target.getId(), List.of("PATIENT")));
    }

    // ---------- tiện ích ----------

    /** Mọi tài khoản có role ADMIN (kể cả seed), để dựng tình huống "admin cuối cùng". */
    private List<User> userRepositoryAdmins() {
        return users.findAll().stream()
                .filter(u -> userRoles.findRoleCodesByUserId(u.getId()).contains("ADMIN"))
                .toList();
    }
}
