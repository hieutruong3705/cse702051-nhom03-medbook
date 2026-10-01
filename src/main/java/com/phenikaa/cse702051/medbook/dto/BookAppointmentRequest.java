package com.phenikaa.cse702051.medbook.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Đặt lịch. Bệnh nhân được lấy từ JWT, KHÔNG nhận từ request (chống đặt hộ người khác).
 */
public record BookAppointmentRequest(
        @NotNull(message = "Phải chọn khung giờ khám")
        Long slotId,

        @NotNull(message = "Phải chọn dịch vụ khám")
        Long serviceId,

        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        String notes
) {
}
