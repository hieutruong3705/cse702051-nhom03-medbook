package com.phenikaa.cse702051.medbook.dto;

import com.phenikaa.cse702051.medbook.model.AppointmentStatus;

import jakarta.validation.constraints.NotNull;

/** Bác sĩ chuyển trạng thái: chỉ IN_PROGRESS (bắt đầu khám) hoặc COMPLETED (hoàn thành). */
public record UpdateAppointmentStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        AppointmentStatus status
) {
}
