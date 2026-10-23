package com.phenikaa.cse702051.medbook.security;

import java.security.Principal;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Principal duy nhất được đặt vào SecurityContext sau khi xác minh JWT.
 * <p>
 * {@link #getName()} trả về username để các đoạn mã cũ dùng
 * {@code authentication.getName()} như username vẫn hoạt động. Mã mới nên lấy
 * danh tính qua {@link CurrentUserService}.
 * <p>
 * {@code roles} không có tiền tố {@code ROLE_} (ví dụ {@code PATIENT}).
 */
public record AuthenticatedUser(Long userId, String username, Set<String> roles) implements Principal {

    public AuthenticatedUser {
        roles = roles == null
                ? Set.of()
                : roles.stream()
                        .map(CurrentUser::normalizeRole)
                        .filter(role -> !role.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String getName() {
        return username;
    }

    public CurrentUser toCurrentUser() {
        return new CurrentUser(userId, roles);
    }
}
