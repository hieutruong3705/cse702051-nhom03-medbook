package com.phenikaa.cse702051.medbook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RescheduleAppointmentRequest(
        @NotNull(message = "Phải chọn khung giờ mới")
        Long newSlotId,

        @Size(max = 500, message = "Lý do đổi lịch tối đa 500 ký tự")
        String reason
) {
}
