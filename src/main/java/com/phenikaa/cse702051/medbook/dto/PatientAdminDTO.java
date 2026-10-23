package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Patient;

/**
 * Bệnh nhân nhìn từ Admin: CHỈ trường hành chính. Không có dị ứng, nhóm máu hay liên hệ khẩn
 * cấp vì Admin không mặc nhiên được đọc thông tin y tế.
 */
public record PatientAdminDTO(
        Long id,
        String patientCode,
        String fullName,
        LocalDate dateOfBirth,
        String genderCode,
        String phone,
        String email,
        String address,
        String status,
        LocalDateTime createdAt
) {
    public static PatientAdminDTO from(Patient patient) {
        return new PatientAdminDTO(
                patient.getId(),
                patient.getPatientCode(),
                patient.getFullName(),
                patient.getDateOfBirth(),
                patient.getGenderCode(),
                patient.getPhone(),
                patient.getEmail(),
                patient.getAddress(),
                patient.getStatus(),
                patient.getCreatedAt());
    }
}
