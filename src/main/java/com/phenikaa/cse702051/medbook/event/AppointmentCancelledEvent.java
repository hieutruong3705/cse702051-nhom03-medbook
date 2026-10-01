package com.phenikaa.cse702051.medbook.event;

import java.time.LocalDate;
import java.time.LocalTime;

/** Phát khi hủy lịch thành công (slot đã được giải phóng); dùng để thông báo và hủy nhắc lịch. */
public record AppointmentCancelledEvent(
        Long appointmentId,
        Long patientId,
        Long doctorId,
        LocalDate date,
        LocalTime startTime,
        String reason
) {
}
