package com.phenikaa.cse702051.medbook.dto.encounter;

import java.time.LocalDateTime;

/**
 * Chi tiết một lần khám. {@code editable} = true khi người xem là bác sĩ phụ trách và lần khám còn OPEN,
 * để giao diện chuyển sang chế độ chỉ đọc mà không phải suy luận lại quy tắc.
 */
public record EncounterDTO(
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
        String clinicalNotes,
        String treatmentPlan,
        String followUpNote,
        String status,
        boolean editable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
