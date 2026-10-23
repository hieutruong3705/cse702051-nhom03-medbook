package com.phenikaa.cse702051.medbook.dto.reception;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lễ tân tạo hồ sơ cho bệnh nhân đến quầy mà chưa có tài khoản. Chỉ nhận thông tin hành chính; hồ sơ không gắn tài
 * khoản đăng nhập nào.
 */
public record ReceptionPatientRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 150, message = "Họ và tên tối đa 150 ký tự")
        String fullName,
        @NotNull(message = "Phải nhập ngày sinh")
        @PastOrPresent(message = "Ngày sinh không được ở tương lai")
        LocalDate dateOfBirth,
        @NotBlank(message = "Phải chọn giới tính")
        @Pattern(regexp = "(?i)^$|^(MALE|FEMALE|OTHER)$", message = "Giới tính phải là MALE, FEMALE hoặc OTHER")
        String genderCode,
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^$|^[0-9+()\\-\\s]{8,30}$", message = "Số điện thoại không hợp lệ")
        String phone,
        @Email(message = "Định dạng email không hợp lệ")
        @Size(max = 190, message = "Email tối đa 190 ký tự")
        String email,
        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address
) {
}
