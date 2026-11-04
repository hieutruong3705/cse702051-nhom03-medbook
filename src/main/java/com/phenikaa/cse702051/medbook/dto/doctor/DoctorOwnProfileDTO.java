package com.phenikaa.cse702051.medbook.dto.doctor;

import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Specialty;

/**
 * Hồ sơ của chính bác sĩ đang đăng nhập: phần công khai cộng số giấy phép và điện thoại (bác sĩ được xem nhưng
 * không tự sửa số giấy phép).
 */
public record DoctorOwnProfileDTO(
        Long id,
        String fullName,
        Long specialtyId,
        String specialtyName,
        String phone,
        String licenseNumber,
        String bio,
        String academicTitle,
        Short yearsOfExperience,
        String education,
        String expertise,
        String workHistory,
        boolean isActive,
        LocalDateTime updatedAt
) {
    public static DoctorOwnProfileDTO from(Doctor doctor) {
        Specialty specialty = doctor.getSpecialty();
        return new DoctorOwnProfileDTO(
                doctor.getId(),
                doctor.getFullName(),
                specialty == null ? null : specialty.getId(),
                specialty == null ? null : specialty.getName(),
                doctor.getPhone(),
                doctor.getLicenseNumber(),
                doctor.getBio(),
                doctor.getAcademicTitle(),
                doctor.getYearsOfExperience(),
                doctor.getEducation(),
                doctor.getExpertise(),
                doctor.getWorkHistory(),
                Boolean.TRUE.equals(doctor.getIsActive()),
                doctor.getUpdatedAt());
    }
}
