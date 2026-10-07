package com.phenikaa.cse702051.medbook.dto.doctor;

import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Specialty;

/**
 * Hồ sơ bác sĩ cho trang quản trị. {@code hasHistory = true} nghĩa là bác sĩ đã có lịch hẹn, ca làm việc hoặc
 * lần khám nên khi "xóa" chỉ bị ngừng hoạt động.
 */
public record DoctorAdminDTO(
        Long id,
        Long userId,
        String username,
        String fullName,
        Long specialtyId,
        String specialtyName,
        String phone,
        String licenseNumber,
        String bio,
        boolean isActive,
        boolean hasHistory,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DoctorAdminDTO from(Doctor doctor, String username, boolean hasHistory) {
        Specialty specialty = doctor.getSpecialty();
        return new DoctorAdminDTO(
                doctor.getId(),
                doctor.getUserId(),
                username,
                doctor.getFullName(),
                specialty == null ? null : specialty.getId(),
                specialty == null ? null : specialty.getName(),
                doctor.getPhone(),
                doctor.getLicenseNumber(),
                doctor.getBio(),
                Boolean.TRUE.equals(doctor.getIsActive()),
                hasHistory,
                doctor.getCreatedAt(),
                doctor.getUpdatedAt());
    }
}
