package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Patient;

public record PatientDTO(
        Long id,
        Long userId,
        String patientCode,
        String fullName,
        LocalDate dateOfBirth,
        String genderCode,
        String phone,
        String email,
        String address,
        String emergencyContactName,
        String emergencyContactPhone,
        String bloodType,
        String allergies,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PatientDTO from(Patient patient) {
        Long userId = patient.getUser() == null ? null : patient.getUser().getId();
        return new PatientDTO(
                patient.getId(),
                userId,
                patient.getPatientCode(),
                patient.getFullName(),
                patient.getDateOfBirth(),
                patient.getGenderCode(),
                patient.getPhone(),
                patient.getEmail(),
                patient.getAddress(),
                patient.getEmergencyContactName(),
                patient.getEmergencyContactPhone(),
                patient.getBloodType(),
                patient.getAllergies(),
                patient.getStatus(),
                patient.getCreatedAt(),
                patient.getUpdatedAt());
    }
}
