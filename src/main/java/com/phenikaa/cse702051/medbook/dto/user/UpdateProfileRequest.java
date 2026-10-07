package com.phenikaa.cse702051.medbook.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sửa thông tin liên hệ của một tài khoản (chính mình ở {@code /users/me}, hoặc Admin sửa cho người khác). Cố ý
 * chỉ có ba trường: tên đăng nhập, vai trò và trạng thái không đổi được qua đây dù client gửi thêm.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 150, message = "Họ và tên tối đa 150 ký tự")
        String fullName,

        @Pattern(regexp = "^$|^[0-9+()\\-\\s]{8,30}$", message = "Số điện thoại không hợp lệ")
        String phone,

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        @Size(max = 190, message = "Email tối đa 190 ký tự")
        String email
) {
}
