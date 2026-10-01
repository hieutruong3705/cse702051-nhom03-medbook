package com.phenikaa.cse702051.medbook.dto.encounter;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bắt đầu một lần khám từ lịch hẹn. Cố ý KHÔNG có {@code doctorId}/{@code medicalRecordId}: bác sĩ
 * lấy từ JWT, bệnh án suy ra từ bệnh nhân của lịch hẹn (trường lạ trong JSON bị bỏ qua).
 */
public record CreateEncounterRequest(
        @NotNull(message = "Lịch hẹn không được để trống")
        Long appointmentId,

        @Size(max = 5000, message = "Lý do khám tối đa 5000 ký tự")
        String chiefComplaint
) {
}
