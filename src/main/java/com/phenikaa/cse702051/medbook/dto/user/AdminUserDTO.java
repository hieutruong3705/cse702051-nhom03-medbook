package com.phenikaa.cse702051.medbook.dto.user;

import java.time.LocalDateTime;
import java.util.List;

import com.phenikaa.cse702051.medbook.model.User;

/**
 * Tài khoản cho trang quản trị: thêm tình trạng khóa. Không bao giờ có băm mật khẩu hay phiên bản token.
 * {@code status} là {@code ACTIVE} hoặc {@code LOCKED} (Admin khóa); {@code lockedUntil} là thời điểm hết khóa tạm
 * do đăng nhập sai nhiều lần.
 */
public record AdminUserDTO(
        Long id,
        String username,
        String email,
        String fullName,
        String phone,
        String status,
        List<String> roles,
        Long patientId,
        Long doctorId,
        LocalDateTime lockedUntil,
        int failedLoginCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminUserDTO from(User user, List<String> roles, Long patientId, Long doctorId) {
        return new AdminUserDTO(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.getStatus(), roles, patientId, doctorId, user.getLockedUntil(),
                user.getFailedLoginCount(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
