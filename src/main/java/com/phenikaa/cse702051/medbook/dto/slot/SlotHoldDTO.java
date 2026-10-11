package com.phenikaa.cse702051.medbook.dto.slot;

import java.time.LocalDateTime;

/**
 * Bản giữ chỗ của người gọi trên một khung giờ. {@code remainingSeconds} tính tại máy chủ lúc trả lời, để giao diện
 * đếm ngược mà không phụ thuộc đồng hồ hay múi giờ của máy người dùng.
 */
public record SlotHoldDTO(
        Long holdId,
        Long slotId,
        LocalDateTime expiresAt,
        long remainingSeconds
) {
}
