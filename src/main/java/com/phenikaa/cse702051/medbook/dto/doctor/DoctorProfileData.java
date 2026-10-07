package com.phenikaa.cse702051.medbook.dto.doctor;

/**
 * Dữ liệu hồ sơ bác sĩ mà module tài khoản truyền vào khi Admin tạo tài khoản bác sĩ
 * ({@code DoctorService.createProfileForUser}). Kiểm tra dữ liệu nằm ở service nhận.
 */
public record DoctorProfileData(
        String fullName,
        Long specialtyId,
        String phone,
        String licenseNumber,
        String bio
) {
}
