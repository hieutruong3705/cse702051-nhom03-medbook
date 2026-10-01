package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Thêm giờ nghỉ vào một ca làm việc đã có. */
public record AddBreakRequest(
        @NotNull(message = "Phải nhập giờ bắt đầu nghỉ")
        LocalTime startTime,

        @NotNull(message = "Phải nhập giờ kết thúc nghỉ")
        LocalTime endTime,

        @Size(max = 255, message = "Lý do nghỉ tối đa 255 ký tự")
        String reason
) {
}
