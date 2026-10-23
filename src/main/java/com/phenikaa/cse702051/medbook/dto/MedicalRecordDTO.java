package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDateTime;

public record MedicalRecordDTO(
        Long id,
        String recordCode,
        Long patientId,
        String patientName,
        String bloodType,
        String chronicConditions,
        String allergyNotes,
        String medicalHistory,
        String currentMedications,
        String status,
        LocalDateTime updatedAt
) {
}
