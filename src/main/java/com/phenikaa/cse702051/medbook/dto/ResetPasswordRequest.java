package com.phenikaa.cse702051.medbook.dto;

import com.phenikaa.cse702051.medbook.security.PasswordPolicy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token đặt lại mật khẩu không được để trống")
        @Size(max = 200, message = "Token không hợp lệ")
        String token,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String newPassword
) {
}
