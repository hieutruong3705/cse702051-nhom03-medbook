package com.phenikaa.cse702051.medbook.dto.user;

import com.phenikaa.cse702051.medbook.security.BcryptLength;
import com.phenikaa.cse702051.medbook.security.PasswordPolicy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin tạo tài khoản với đúng một vai trò ({@code PATIENT}, {@code DOCTOR} hoặc {@code ADMIN}).
 * {@code doctorProfile} bắt buộc khi vai trò là {@code DOCTOR} và không được gửi với vai trò khác.
 */
public record AdminCreateUserRequest(
        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$",
                message = "Tên đăng nhập chỉ gồm chữ cái, chữ số, dấu chấm, gạch dưới và gạch ngang")
        String username,

        @NotBlank(message = "Mật khẩu không được để trống")
        @BcryptLength
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password,

        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 150, message = "Họ và tên tối đa 150 ký tự")
        String fullName,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        @Size(max = 190, message = "Email tối đa 190 ký tự")
        String email,

        @Pattern(regexp = "^$|^[0-9+()\\-\\s]{8,30}$", message = "Số điện thoại không hợp lệ")
        String phone,

        @NotBlank(message = "Phải chọn vai trò")
        String role,

        @Valid
        DoctorProfileInput doctorProfile
) {

    /** Hồ sơ bác sĩ tạo cùng lúc với tài khoản. Họ tên lấy từ tài khoản. */
    public record DoctorProfileInput(
            @NotNull(message = "Phải chọn chuyên khoa")
            Long specialtyId,

            @NotBlank(message = "Số giấy phép hành nghề không được để trống")
            @Size(max = 50, message = "Số giấy phép hành nghề tối đa 50 ký tự")
            String licenseNumber,

            @Pattern(regexp = "^$|^[0-9+()\\-\\s]{8,20}$", message = "Số điện thoại không hợp lệ")
            String phone,

            @Size(max = 2000, message = "Giới thiệu tối đa 2000 ký tự")
            String bio
    ) {
    }
}
