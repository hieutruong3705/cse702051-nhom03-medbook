package com.phenikaa.cse702051.medbook.dto.schedule;

import java.time.LocalTime;

/**
 * Một slot trong ca làm việc của bác sĩ. {@code status} là {@code AVAILABLE} hoặc {@code BOOKED};
 * {@code appointmentId} là lịch hẹn đang giữ chỗ ({@code null} khi slot còn trống). Không có thông tin bệnh nhân:
 * muốn xem thì mở lịch hẹn.
 */
public record ScheduleSlotDTO(
        Long id,
        LocalTime startTime,
        LocalTime endTime,
        String status,
        Long appointmentId
) {
}
