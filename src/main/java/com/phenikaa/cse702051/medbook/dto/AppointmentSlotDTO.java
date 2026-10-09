package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Khung giờ khám trả về cho giao diện. Cố ý không phơi bày entity (quan hệ lazy tới bác sĩ, cột version).
 * {@code status}: AVAILABLE (trống), BOOKED (đã đặt) hoặc CANCELLED (đã gỡ khỏi lịch).
 * {@code heldByMe} và {@code holdExpiresAt} chỉ có nghĩa ở danh sách giờ trống: khung giờ mà chính người xem đang
 * giữ chỗ. Khung giờ người khác đang giữ không xuất hiện trong danh sách đó.
 */
public record AppointmentSlotDTO(
        Long id,
        Long doctorId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        String status,
        boolean heldByMe,
        LocalDateTime holdExpiresAt
) {

    public AppointmentSlotDTO(Long id, Long doctorId, LocalDate slotDate, LocalTime startTime, LocalTime endTime,
            String status) {
        this(id, doctorId, slotDate, startTime, endTime, status, false, null);
    }
}
