package com.phenikaa.cse702051.medbook.event;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Phát khi đặt lịch thành công. Chỉ chứa dữ liệu thuần (không giữ entity lazy) để bộ lắng nghe
 * {@code @TransactionalEventListener(AFTER_COMMIT)} dùng được sau khi phiên JPA đã đóng.
 */
public record AppointmentBookedEvent(
        Long appointmentId,
        Long patientId,
        Long doctorId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {
}
