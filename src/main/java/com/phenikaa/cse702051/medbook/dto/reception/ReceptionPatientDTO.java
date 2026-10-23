package com.phenikaa.cse702051.medbook.dto.reception;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Patient;

/**
 * Bệnh nhân nhìn từ quầy lễ tân: CHỈ trường hành chính. Cố ý không có dị ứng, nhóm máu hay liên hệ khẩn cấp vì lễ
 * tân không được đọc thông tin y tế. {@code hasAccount} cho biết bệnh nhân có tài khoản đăng nhập hay là hồ sơ do
 * quầy tạo.
 */
public record ReceptionPatientDTO(
        Long id,
        String patientCode,
        String fullName,
        LocalDate dateOfBirth,
        String genderCode,
        String phone,
        String email,
        String address,
        String status,
        boolean hasAccount,
        LocalDateTime createdAt
) {
    public static ReceptionPatientDTO from(Patient patient) {
        return new ReceptionPatientDTO(
                patient.getId(),
                patient.getPatientCode(),
                patient.getFullName(),
                patient.getDateOfBirth(),
                patient.getGenderCode(),
                patient.getPhone(),
                patient.getEmail(),
                patient.getAddress(),
                patient.getStatus(),
                // quan hệ lazy: chỉ so sánh tham chiếu với null nên không nạp bảng users
                patient.getUser() != null,
                patient.getCreatedAt());
    }
}
