package com.phenikaa.cse702051.medbook.dto.doctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin tạo hoặc sửa hồ sơ bác sĩ. {@code userId} (tài khoản đã có vai trò DOCTOR và chưa có hồ sơ) chỉ dùng khi
 * tạo; khi sửa bị bỏ qua vì hồ sơ không đổi được sang tài khoản khác.
 */
public record AdminDoctorRequest(
        Long userId,

        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
        String fullName,

        @NotNull(message = "Phải chọn chuyên khoa")
        Long specialtyId,

        @Pattern(regexp = "^$|^[0-9+()\\-\\s]{8,20}$", message = "Số điện thoại không hợp lệ")
        String phone,

        @NotBlank(message = "Số giấy phép hành nghề không được để trống")
        @Size(max = 50, message = "Số giấy phép hành nghề tối đa 50 ký tự")
        String licenseNumber,

        @Size(max = 2000, message = "Giới thiệu tối đa 2000 ký tự")
        String bio
) {
}
