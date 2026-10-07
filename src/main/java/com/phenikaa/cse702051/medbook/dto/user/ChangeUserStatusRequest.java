package com.phenikaa.cse702051.medbook.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Khóa ({@code LOCKED}) hoặc mở ({@code ACTIVE}) một tài khoản; {@code reason} được lưu trong audit. */
public record ChangeUserStatusRequest(
        @NotBlank(message = "Phải chọn trạng thái")
        String status,

        @Size(max = 255, message = "Lý do tối đa 255 ký tự")
        String reason
) {
}
