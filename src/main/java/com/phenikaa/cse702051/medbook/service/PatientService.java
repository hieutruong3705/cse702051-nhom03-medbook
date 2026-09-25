package com.phenikaa.cse702051.medbook.service;

import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.dto.PatientDTO;
import com.phenikaa.cse702051.medbook.dto.PatientUpdateRequest;
import com.phenikaa.cse702051.medbook.exception.ApiException;
import com.phenikaa.cse702051.medbook.exception.ErrorCode;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final CurrentUserService currentUserService;

    public PatientService(PatientRepository patientRepository, CurrentUserService currentUserService) {
        this.patientRepository = patientRepository;
        this.currentUserService = currentUserService;
    }

    public PatientDTO getCurrentPatient(HttpServletRequest request) {
        CurrentUser currentUser = currentUserService.requireCurrentUser(request);
        requirePatientRole(currentUser);
        return PatientDTO.from(findPatientByCurrentUser(currentUser));
    }

    public PatientDTO updateCurrentPatient(PatientUpdateRequest updateRequest, HttpServletRequest request) {
        CurrentUser currentUser = currentUserService.requireCurrentUser(request);
        requirePatientRole(currentUser);

        Patient patient = findPatientByCurrentUser(currentUser);
        applyUpdate(patient, updateRequest);
        return PatientDTO.from(patientRepository.save(patient));
    }

    public Patient findPatientByCurrentUser(CurrentUser currentUser) {
        return patientRepository.findByUserId(currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay ho so benh nhan cua tai khoan hien tai"));
    }

    private void requirePatientRole(CurrentUser currentUser) {
        if (!currentUser.hasRole("PATIENT")) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Chi benh nhan moi duoc thuc hien thao tac nay");
        }
    }

    private void applyUpdate(Patient patient, PatientUpdateRequest updateRequest) {
        if (updateRequest.fullName() != null) {
            patient.setFullName(updateRequest.fullName());
        }
        if (updateRequest.dateOfBirth() != null) {
            patient.setDateOfBirth(updateRequest.dateOfBirth());
        }
        if (updateRequest.genderCode() != null) {
            patient.setGenderCode(updateRequest.genderCode());
        }
        if (updateRequest.phone() != null) {
            patient.setPhone(updateRequest.phone());
        }
        if (updateRequest.email() != null) {
            patient.setEmail(updateRequest.email());
        }
        if (updateRequest.address() != null) {
            patient.setAddress(updateRequest.address());
        }
        if (updateRequest.emergencyContactName() != null) {
            patient.setEmergencyContactName(updateRequest.emergencyContactName());
        }
        if (updateRequest.emergencyContactPhone() != null) {
            patient.setEmergencyContactPhone(updateRequest.emergencyContactPhone());
        }
        if (updateRequest.bloodType() != null) {
            patient.setBloodType(updateRequest.bloodType());
        }
        if (updateRequest.allergies() != null) {
            patient.setAllergies(updateRequest.allergies());
        }
    }
}
