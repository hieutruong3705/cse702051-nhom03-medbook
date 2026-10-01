package com.phenikaa.cse702051.medbook.security;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Nguồn danh tính duy nhất của toàn hệ thống: chỉ đọc {@link AuthenticatedUser}
 * do {@code JwtAuthenticationFilter} đặt vào SecurityContext. Không tin bất kỳ
 * header hay tham số nào do client tự đặt.
 */
@Service
public class CurrentUserService {

    public Optional<CurrentUser> findCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.userId() != null) {
            return Optional.of(user.toCurrentUser());
        }
        return Optional.empty();
    }

    public CurrentUser requireCurrentUser() {
        return findCurrentUser()
                .orElseThrow(() -> new UnauthorizedException("Bạn chưa đăng nhập hoặc phiên đã hết hạn!"));
    }

    /**
     * Giữ lại để các service cũ ({@code PatientService}, {@code AuditLogService})
     * không phải đổi chữ ký. Tham số {@code request} bị bỏ qua.
     */
    public CurrentUser requireCurrentUser(HttpServletRequest request) {
        return requireCurrentUser();
    }

    public Long requireUserId() {
        return requireCurrentUser().userId();
    }

    public boolean isAdmin() {
        return findCurrentUser().map(user -> user.hasRole("ADMIN")).orElse(false);
    }

    public boolean isDoctor() {
        return findCurrentUser().map(user -> user.hasRole("DOCTOR")).orElse(false);
    }

    public boolean isPatient() {
        return findCurrentUser().map(user -> user.hasRole("PATIENT")).orElse(false);
    }

    /**
     * Yêu cầu người dùng hiện tại có ít nhất một trong các role; nếu không → 403.
     */
    public CurrentUser requireRole(String... roles) {
        CurrentUser user = requireCurrentUser();
        boolean allowed = Arrays.stream(roles).anyMatch(user::hasRole);
        if (!allowed) {
            throw new ForbiddenException("Bạn không có quyền thực hiện thao tác này!");
        }
        return user;
    }
}
