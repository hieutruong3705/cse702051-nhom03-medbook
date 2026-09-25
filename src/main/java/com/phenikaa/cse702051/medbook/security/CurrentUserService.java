package com.phenikaa.cse702051.medbook.security;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.exception.ApiException;
import com.phenikaa.cse702051.medbook.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class CurrentUserService {

    private static final String MOCK_USER_ID_HEADER = "X-MedBook-User-Id";
    private static final String MOCK_ROLES_HEADER = "X-MedBook-Roles";

    public CurrentUser requireCurrentUser(HttpServletRequest request) {
        Long userId = resolveUserId(request)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Chua dang nhap"));
        Set<String> roles = resolveRoles(request);
        return new CurrentUser(userId, roles);
    }

    private Optional<Long> resolveUserId(HttpServletRequest request) {
        Optional<Long> mockUserId = parseLong(request.getHeader(MOCK_USER_ID_HEADER));
        if (mockUserId.isPresent()) {
            return mockUserId;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        return parseLong(authentication.getName());
    }

    private Set<String> resolveRoles(HttpServletRequest request) {
        Set<String> roles = new LinkedHashSet<>();
        String mockRoles = request.getHeader(MOCK_ROLES_HEADER);
        if (mockRoles != null && !mockRoles.isBlank()) {
            roles.addAll(Arrays.stream(mockRoles.split(","))
                    .map(CurrentUser::normalizeRole)
                    .filter(role -> !role.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String role = CurrentUser.normalizeRole(authority.getAuthority());
                if (!role.isBlank()) {
                    roles.add(role);
                }
            }
        }

        return Set.copyOf(roles);
    }

    private Optional<Long> parseLong(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
