package com.phenikaa.cse702051.medbook.service;

import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.dto.MedicalRecordDTO;
import com.phenikaa.cse702051.medbook.exception.ApiException;
import com.phenikaa.cse702051.medbook.exception.ErrorCode;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalRecordRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final DoctorRepository doctorRepository;
    private final EncounterRepository encounterRepository;
    private final PatientService patientService;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;

    public MedicalRecordService(
            MedicalRecordRepository medicalRecordRepository,
            DoctorRepository doctorRepository,
            EncounterRepository encounterRepository,
            PatientService patientService,
            AuditLogService auditLogService,
            CurrentUserService currentUserService) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.doctorRepository = doctorRepository;
        this.encounterRepository = encounterRepository;
        this.patientService = patientService;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
    }

    public MedicalRecordDTO getCurrentPatientMedicalRecord(HttpServletRequest request) {
        CurrentUser currentUser = currentUserService.requireCurrentUser(request);
        if (!currentUser.hasRole("PATIENT")) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Chi benh nhan moi duoc xem benh an cua minh");
        }

        Patient patient = patientService.findPatientByCurrentUser(currentUser);
        MedicalRecord medicalRecord = medicalRecordRepository.findByPatientId(patient.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay benh an cua benh nhan hien tai"));
        auditLogService.recordMedicalRecordView(currentUser, medicalRecord, request);
        return MedicalRecordDTO.from(medicalRecord);
    }

    public MedicalRecordDTO getMedicalRecordById(Long id, HttpServletRequest request) {
        CurrentUser currentUser = currentUserService.requireCurrentUser(request);
        MedicalRecord medicalRecord = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay benh an"));

        verifyMedicalRecordAccess(currentUser, medicalRecord);
        auditLogService.recordMedicalRecordView(currentUser, medicalRecord, request);
        return MedicalRecordDTO.from(medicalRecord);
    }

    private void verifyMedicalRecordAccess(CurrentUser currentUser, MedicalRecord medicalRecord) {
        if (currentUser.hasRole("ADMIN")) {
            return;
        }

        if (currentUser.hasRole("PATIENT") && isOwnerPatient(currentUser, medicalRecord)) {
            return;
        }

        if (currentUser.hasRole("DOCTOR") && isResponsibleDoctor(currentUser, medicalRecord)) {
            return;
        }

        throw new ApiException(ErrorCode.FORBIDDEN, "Khong co quyen truy cap benh an nay");
    }

    private boolean isOwnerPatient(CurrentUser currentUser, MedicalRecord medicalRecord) {
        Patient patient = medicalRecord.getPatient();
        return patient != null
                && patient.getUser() != null
                && currentUser.userId().equals(patient.getUser().getId());
    }

    private boolean isResponsibleDoctor(CurrentUser currentUser, MedicalRecord medicalRecord) {
        Doctor doctor = doctorRepository.findByUserId(currentUser.userId()).orElse(null);
        return doctor != null
                && encounterRepository.existsByMedicalRecordIdAndDoctorId(medicalRecord.getId(), doctor.getId());
    }
}
