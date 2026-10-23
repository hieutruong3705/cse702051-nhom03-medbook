package com.phenikaa.cse702051.medbook.dto.reception;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lễ tân hủy lịch hộ: bắt buộc nêu lý do. */
public record ReceptionCancelRequest(
        @NotBlank(message = "Phải nhập lý do hủy lịch")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
