package com.phenikaa.cse702051.medbook.dto.reception;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lễ tân đổi lịch hộ: bắt buộc nêu lý do. Lý do không được lưu (không có cột) và không vào nhật ký; nó chỉ buộc
 * người thao tác phải nêu trước khi đổi lịch của người khác.
 */
public record ReceptionRescheduleRequest(
        @NotNull(message = "Phải chọn khung giờ mới")
        Long newSlotId,
        @NotBlank(message = "Phải nhập lý do đổi lịch")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
