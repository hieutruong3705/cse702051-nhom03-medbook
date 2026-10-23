package com.phenikaa.cse702051.medbook.dto.doctor;

import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Specialty;

/**
 * Hồ sơ bác sĩ trên trang công khai. Cố ý KHÔNG có tài khoản ({@code userId}), số giấy phép hành nghề hay số
 * điện thoại: đó là dữ liệu nội bộ chỉ Admin thấy.
 */
public record DoctorPublicDTO(
        Long id,
        String fullName,
        Long specialtyId,
        String specialtyName,
        String bio
) {
    public static DoctorPublicDTO from(Doctor doctor) {
        Specialty specialty = doctor.getSpecialty();
        return new DoctorPublicDTO(
                doctor.getId(),
                doctor.getFullName(),
                specialty == null ? null : specialty.getId(),
                specialty == null ? null : specialty.getName(),
                doctor.getBio());
    }
}
