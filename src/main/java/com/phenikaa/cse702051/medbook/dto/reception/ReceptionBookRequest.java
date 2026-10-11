package com.phenikaa.cse702051.medbook.dto.reception;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lễ tân đặt lịch hộ. {@code patientId} là bệnh nhân ĐƯỢC đặt lịch, không phải người thao tác: người thao tác luôn
 * lấy từ JWT. Ghi chú chỉ ghi một chiều; lễ tân không đọc lại được.
 */
public record ReceptionBookRequest(
        @NotNull(message = "Phải chọn bệnh nhân")
        Long patientId,
        @NotNull(message = "Phải chọn khung giờ khám")
        Long slotId,
        @NotNull(message = "Phải chọn dịch vụ khám")
        Long serviceId,
        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        String notes,
        /** Tùy chọn: bản giữ chỗ lễ tân nhận được khi chọn khung giờ. Đã hết hạn → 410. */
        Long holdId
) {
}
