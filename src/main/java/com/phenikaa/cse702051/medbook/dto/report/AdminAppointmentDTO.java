package com.phenikaa.cse702051.medbook.dto.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.phenikaa.cse702051.medbook.model.AppointmentStatus;

/**
 * Lịch hẹn cho trang quản trị: chỉ thông tin hành chính. Cố ý KHÔNG có ghi chú của bệnh nhân, lý do hủy hay bất
 * kỳ nội dung lâm sàng nào (Admin không được đọc dữ liệu khám bệnh).
 */
public record AdminAppointmentDTO(
        Long id,
        String patientName,
        String patientCode,
        String doctorName,
        String specialtyName,
        String serviceName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        AppointmentStatus status,
        LocalDateTime createdAt
) {
}
