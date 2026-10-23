package com.phenikaa.cse702051.medbook.event;

import java.time.LocalDate;
import java.time.LocalTime;

/** Phát khi đổi lịch thành công: kèm khung giờ cũ và mới để thông báo và hủy/đặt lại nhắc lịch. */
public record AppointmentRescheduledEvent(
        Long appointmentId,
        Long patientId,
        Long oldDoctorId,
        LocalDate oldDate,
        LocalTime oldStartTime,
        Long newDoctorId,
        LocalDate newDate,
        LocalTime newStartTime,
        LocalTime newEndTime
) {
}
