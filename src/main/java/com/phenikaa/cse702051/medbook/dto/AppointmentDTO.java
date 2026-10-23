package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.phenikaa.cse702051.medbook.model.AppointmentStatus;

/**
 * Lịch hẹn trả ra API. Không chứa entity; chỉ dữ liệu cần hiển thị cho chủ lịch hoặc bác sĩ
 * phụ trách (Admin dùng DTO hành chính riêng, không có {@code notes}).
 */
public record AppointmentDTO(
        Long id,
        AppointmentStatus status,
        String notes,
        String cancelReason,
        Long patientId,
        String patientName,
        String patientCode,
        Long doctorId,
        String doctorName,
        Long specialtyId,
        String specialtyName,
        Long slotId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Long serviceId,
        String serviceName,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt
) {
}
