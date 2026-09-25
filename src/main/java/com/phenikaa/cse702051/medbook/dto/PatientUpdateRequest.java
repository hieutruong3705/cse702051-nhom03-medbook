package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDate;

public record PatientUpdateRequest(
        String fullName,
        LocalDate dateOfBirth,
        String genderCode,
        String phone,
        String email,
        String address,
        String emergencyContactName,
        String emergencyContactPhone,
        String bloodType,
        String allergies
) {
}
