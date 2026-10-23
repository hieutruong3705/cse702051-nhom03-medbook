package com.phenikaa.cse702051.medbook.dto.encounter;

import java.time.LocalDateTime;

/** Dòng tóm tắt trong danh sách lần khám (lịch sử khám của bệnh nhân, tra cứu của bác sĩ). */
public record EncounterSummaryDTO(
        Long id,
        Long appointmentId,
        Long medicalRecordId,
        Long patientId,
        String patientName,
        Long doctorId,
        String doctorName,
        LocalDateTime encounterAt,
        String chiefComplaint,
        String diagnosis,
        String status
) {
}
