package com.phenikaa.cse702051.medbook.security;

import java.util.Set;

public record CurrentUser(
        Long userId,
        Set<String> roles
) {
    public boolean hasRole(String role) {
        return roles.contains(normalizeRole(role));
    }

    public static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        String normalized = role.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized.substring("ROLE_".length()) : normalized;
    }
}
