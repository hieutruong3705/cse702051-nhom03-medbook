package com.phenikaa.cse702051.medbook.dto.schedule;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Đăng ký nghỉ cả ngày. Bác sĩ lấy từ JWT, KHÔNG nhận từ request. */
public record DayOffRequest(
        @NotNull(message = "Phải chọn ngày nghỉ")
        LocalDate date,

        @Size(max = 255, message = "Lý do nghỉ tối đa 255 ký tự")
        String reason
) {
}
