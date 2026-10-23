package com.phenikaa.cse702051.medbook.dto.user;

import java.time.LocalDateTime;
import java.util.List;

import com.phenikaa.cse702051.medbook.model.User;

/**
 * Hồ sơ tài khoản của người đang đăng nhập. Không bao giờ có băm mật khẩu hay phiên bản token.
 * {@code patientId}/{@code doctorId} là {@code null} khi tài khoản chưa có hồ sơ tương ứng.
 */
public record UserProfileDTO(
        Long id,
        String username,
        String email,
        String fullName,
        String phone,
        String status,
        List<String> roles,
        Long patientId,
        Long doctorId,
        LocalDateTime createdAt
) {
    public static UserProfileDTO from(User user, List<String> roles, Long patientId, Long doctorId) {
        return new UserProfileDTO(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.getStatus(), roles, patientId, doctorId, user.getCreatedAt());
    }
}
