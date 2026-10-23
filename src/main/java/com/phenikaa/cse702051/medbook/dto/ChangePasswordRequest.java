package com.phenikaa.cse702051.medbook.dto;

import com.phenikaa.cse702051.medbook.security.PasswordPolicy;
import com.phenikaa.cse702051.medbook.security.BcryptLength;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank(message = "Mật khẩu cũ không được để trống")
        String oldPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @BcryptLength
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String newPassword
) {
}
