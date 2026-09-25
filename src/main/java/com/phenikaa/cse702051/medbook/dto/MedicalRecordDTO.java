package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.model.Patient;

public record MedicalRecordDTO(
        Long id,
        Long patientId,
        String patientCode,
        String patientName,
        String recordCode,
        String bloodType,
        String chronicConditions,
        String allergyNotes,
        String medicalHistory,
        String currentMedications,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MedicalRecordDTO from(MedicalRecord medicalRecord) {
        Patient patient = medicalRecord.getPatient();
        return new MedicalRecordDTO(
                medicalRecord.getId(),
                patient == null ? null : patient.getId(),
                patient == null ? null : patient.getPatientCode(),
                patient == null ? null : patient.getFullName(),
                medicalRecord.getRecordCode(),
                medicalRecord.getBloodType(),
                medicalRecord.getChronicConditions(),
                medicalRecord.getAllergyNotes(),
                medicalRecord.getMedicalHistory(),
                medicalRecord.getCurrentMedications(),
                medicalRecord.getStatus(),
                medicalRecord.getCreatedAt(),
                medicalRecord.getUpdatedAt());
    }
}
