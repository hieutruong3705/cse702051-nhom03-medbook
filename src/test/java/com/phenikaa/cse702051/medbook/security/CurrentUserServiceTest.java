package com.phenikaa.cse702051.medbook.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.UnauthorizedException;

class CurrentUserServiceTest {

    private final CurrentUserService service = new CurrentUserService();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId, String username, String... roles) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, username, Set.of(roles));
        List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @Test
    @DisplayName("Đọc userId và roles từ AuthenticatedUser trong SecurityContext")
    void readsIdentityFromPrincipal() {
        authenticateAs(4L, "patient1", "PATIENT");

        CurrentUser user = service.requireCurrentUser();

        assertEquals(4L, user.userId());
        assertTrue(user.hasRole("PATIENT"));
        assertTrue(user.hasRole("ROLE_PATIENT"));
        assertFalse(user.hasRole("ADMIN"));
        assertTrue(service.isPatient());
        assertFalse(service.isAdmin());
        assertFalse(service.isDoctor());
    }

    @Test
    @DisplayName("Header X-MedBook-* trên request bị bỏ qua hoàn toàn")
    void requestHeadersAreIgnored() {
        authenticateAs(4L, "patient1", "PATIENT");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-MedBook-User-Id", "1");
        request.addHeader("X-MedBook-Roles", "ADMIN");

        CurrentUser user = service.requireCurrentUser(request);

        assertEquals(4L, user.userId());
        assertFalse(user.hasRole("ADMIN"));
    }

    @Test
    @DisplayName("Không đăng nhập → UnauthorizedException; chỉ có header giả cũng không được")
    void unauthenticatedIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-MedBook-User-Id", "4");
        request.addHeader("X-MedBook-Roles", "PATIENT");

        assertThrows(UnauthorizedException.class, () -> service.requireCurrentUser(request));
        assertTrue(service.findCurrentUser().isEmpty());
        assertFalse(service.isAdmin());
    }

    @Test
    @DisplayName("Anonymous và principal không phải AuthenticatedUser đều bị từ chối")
    void anonymousAndForeignPrincipalsAreRejected() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThrows(UnauthorizedException.class, service::requireCurrentUser);

        // Principal kiểu chuỗi (kiểu cũ) không còn được chấp nhận làm danh tính
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "patient1", null, AuthorityUtils.createAuthorityList("ROLE_PATIENT")));
        assertThrows(UnauthorizedException.class, service::requireCurrentUser);
    }

    @Test
    @DisplayName("requireRole: đúng vai trò trả người dùng, sai vai trò ném ForbiddenException")
    void requireRoleEnforcesRoles() {
        authenticateAs(2L, "doctor1", "DOCTOR");

        assertEquals(2L, service.requireRole("DOCTOR", "ADMIN").userId());
        assertThrows(ForbiddenException.class, () -> service.requireRole("ADMIN"));
    }

    @Test
    @DisplayName("AuthenticatedUser chuẩn hóa role (bỏ tiền tố ROLE_, viết hoa) và getName() là username")
    void authenticatedUserNormalizesRoles() {
        AuthenticatedUser user = new AuthenticatedUser(9L, "someone", Set.of("role_doctor", " ADMIN ", ""));

        assertEquals("someone", user.getName());
        assertEquals(Set.of("DOCTOR", "ADMIN"), user.roles());
    }
}
