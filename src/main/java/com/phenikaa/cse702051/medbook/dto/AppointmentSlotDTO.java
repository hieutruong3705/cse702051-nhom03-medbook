package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Khung giờ khám trả về cho giao diện. Cố ý không phơi bày entity (quan hệ lazy tới bác sĩ, cột version).
 * {@code status}: AVAILABLE (trống), BOOKED (đã đặt) hoặc CANCELLED (đã gỡ khỏi lịch).
 */
public record AppointmentSlotDTO(
        Long id,
        Long doctorId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        String status
) {
}
