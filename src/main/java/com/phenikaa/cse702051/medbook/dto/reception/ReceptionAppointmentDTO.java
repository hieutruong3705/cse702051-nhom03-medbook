package com.phenikaa.cse702051.medbook.dto.reception;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.phenikaa.cse702051.medbook.model.AppointmentStatus;

/**
 * Lịch hẹn nhìn từ quầy lễ tân: thông tin hành chính đủ để tiếp đón và gọi điện cho bệnh nhân. Cố ý KHÔNG có ghi chú
 * của bệnh nhân hay lý do hủy (lễ tân không đọc nội dung liên quan tới khám bệnh).
 */
public record ReceptionAppointmentDTO(
        Long id,
        AppointmentStatus status,
        Long patientId,
        String patientName,
        String patientCode,
        String patientPhone,
        Long doctorId,
        String doctorName,
        Long specialtyId,
        String specialtyName,
        Long serviceId,
        String serviceName,
        Long slotId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt
) {
}
