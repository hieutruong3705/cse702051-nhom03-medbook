package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.MedicalRecordDTO;
import com.phenikaa.cse702051.medbook.dto.UpdateMedicalRecordCommand;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.MedicalRecordRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Bệnh án (YCCN-07, 19, 23). Mỗi bệnh nhân có đúng một bệnh án.
 *
 * <p>Quy tắc đọc: bệnh nhân chỉ đọc của chính mình; bác sĩ chỉ đọc bệnh nhân thuộc phạm vi
 * phụ trách ({@link DoctorScopePolicy}); Admin không đọc được nội dung. Mọi lần đọc thành công
 * đều ghi audit {@code MEDICAL_RECORD_VIEW}, mọi lần bị từ chối ghi {@code ACCESS_DENIED}.
 *
 * <p>Nhóm phương thức "nội bộ" ({@code getOrCreateByPatientId}, {@code createForPatient},
 * {@code updateClinicalSummary}) dành cho module khác (Auth, luồng khám của Dev 4).
 */
@Service
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepository patientRepository;
    private final CurrentUserService currentUserService;
    private final CurrentActorService currentActorService;
    private final DoctorScopePolicy doctorScopePolicy;
    private final AuditLogService auditLogService;

    public MedicalRecordService(
            MedicalRecordRepository medicalRecordRepository,
            PatientRepository patientRepository,
            CurrentUserService currentUserService,
            CurrentActorService currentActorService,
            DoctorScopePolicy doctorScopePolicy,
            AuditLogService auditLogService) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.patientRepository = patientRepository;
        this.currentUserService = currentUserService;
        this.currentActorService = currentActorService;
        this.doctorScopePolicy = doctorScopePolicy;
        this.auditLogService = auditLogService;
    }

    // ========== Nội bộ: dành cho module khác, KHÔNG kiểm quyền người gọi ==========

    /** Tìm bệnh án theo ID — dùng nội bộ bởi EncounterService/EncounterAccessPolicy. */
    @Transactional(readOnly = true)
    public MedicalRecord findById(Long id) {
        return medicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hồ sơ bệnh án với ID: " + id));
    }

    /** Tìm bệnh án theo patientId — dùng nội bộ. */
    @Transactional(readOnly = true)
    public MedicalRecord findByPatientId(Long patientId) {
        return medicalRecordRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bệnh án cho bệnh nhân ID: " + patientId));
    }

    /** Bệnh án của bệnh nhân, tự tạo nếu chưa có (bệnh nhân tạo trước khi có cơ chế tự tạo). */
    @Transactional
    public MedicalRecord getOrCreateByPatientId(Long patientId) {
        return medicalRecordRepository.findByPatientId(patientId)
                .orElseGet(() -> createForPatient(patientRepository.findById(patientId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Không tìm thấy bệnh nhân ID: " + patientId))));
    }

    /** Tạo bệnh án rỗng cho bệnh nhân mới (idempotent). Gọi trong cùng giao dịch với việc tạo Patient. */
    @Transactional
    public MedicalRecord createForPatient(Patient patient) {
        return medicalRecordRepository.findByPatientId(patient.getId())
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    MedicalRecord record = MedicalRecord.builder()
                            .patient(patient)
                            .recordCode(String.format("MR%06d", patient.getId()))
                            .bloodType(patient.getBloodType())
                            .allergyNotes(patient.getAllergies())
                            .status("ACTIVE")
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return medicalRecordRepository.save(record);
                });
    }

    /**
     * Cập nhật tóm tắt bệnh án từ luồng khám. Chỉ bác sĩ đang phụ trách bệnh nhân mới được
     * ghi; chỉ các trường trong {@link UpdateMedicalRecordCommand} được phép đổi.
     */
    @Transactional
    public MedicalRecordDTO updateClinicalSummary(Long recordId, UpdateMedicalRecordCommand command) {
        CurrentUser user = currentUserService.requireCurrentUser();
        MedicalRecord record = findById(recordId);

        boolean allowed = user.hasRole("DOCTOR")
                && currentActorService.findCurrentDoctorId()
                        .map(doctorId -> doctorScopePolicy.isResponsible(doctorId, record.getPatientId()))
                        .orElse(false);
        if (!allowed) {
            auditLogService.recordAccessDenied(AuditActions.ENTITY_MEDICAL_RECORDS, recordId,
                    "Ghi bệnh án khi không phụ trách bệnh nhân");
            throw new ForbiddenException("Bạn không có quyền cập nhật hồ sơ bệnh án này!");
        }

        if (command.bloodType() != null) {
            record.setBloodType(command.bloodType());
        }
        if (command.chronicConditions() != null) {
            record.setChronicConditions(command.chronicConditions());
        }
        if (command.allergyNotes() != null) {
            record.setAllergyNotes(command.allergyNotes());
        }
        if (command.medicalHistory() != null) {
            record.setMedicalHistory(command.medicalHistory());
        }
        if (command.currentMedications() != null) {
            record.setCurrentMedications(command.currentMedications());
        }
        record.setUpdatedAt(LocalDateTime.now());
        return toDTO(medicalRecordRepository.save(record));
    }

    // ========== API: luôn kiểm quyền theo bản ghi và ghi audit ==========

    /** Bệnh án của bệnh nhân đang đăng nhập (YCCN-19). */
    @Transactional
    public MedicalRecordDTO getMyRecord() {
        currentUserService.requireRole("PATIENT");
        Long patientId = currentActorService.requireCurrentPatientId();
        MedicalRecord record = getOrCreateByPatientId(patientId);
        auditLogService.recordMedicalRecordView(record);
        return toDTO(record);
    }

    /** Chi tiết bệnh án theo ID: chủ sở hữu hoặc bác sĩ phụ trách (YCCN-07). */
    @Transactional
    public MedicalRecordDTO getRecordForUser(Long id) {
        CurrentUser user = currentUserService.requireCurrentUser();
        MedicalRecord record = findById(id);
        assertReadableBy(user, record);
        auditLogService.recordMedicalRecordView(record);
        return toDTO(record);
    }

    /** Bệnh án của một bệnh nhân, dành cho bác sĩ phụ trách ({@code GET /patients/{id}/medical-records}). */
    @Transactional
    public MedicalRecordDTO getRecordOfPatient(Long patientId) {
        CurrentUser user = currentUserService.requireCurrentUser();
        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Không tìm thấy bệnh nhân ID: " + patientId);
        }
        MedicalRecord record = findByPatientId(patientId);
        assertReadableBy(user, record);
        auditLogService.recordMedicalRecordView(record);
        return toDTO(record);
    }

    /**
     * Ném {@link ForbiddenException} (và ghi ACCESS_DENIED) nếu người dùng không phải chủ sở hữu
     * hoặc bác sĩ phụ trách của bệnh án.
     */
    public void assertReadableBy(CurrentUser user, MedicalRecord record) {
        boolean isOwner = user.hasRole("PATIENT")
                && currentActorService.findCurrentPatientId()
                        .map(patientId -> patientId.equals(record.getPatientId()))
                        .orElse(false);
        boolean isResponsibleDoctor = user.hasRole("DOCTOR")
                && currentActorService.findCurrentDoctorId()
                        .map(doctorId -> doctorScopePolicy.isResponsible(doctorId, record.getPatientId()))
                        .orElse(false);

        if (isOwner || isResponsibleDoctor) {
            return;
        }

        boolean adminOnly = user.hasRole("ADMIN") && !user.hasRole("DOCTOR") && !user.hasRole("PATIENT");
        auditLogService.recordAccessDenied(AuditActions.ENTITY_MEDICAL_RECORDS, record.getId(),
                adminOnly ? "Quản trị viên không được đọc nội dung bệnh án" : "Không phải chủ sở hữu hoặc bác sĩ phụ trách");
        throw new ForbiddenException(adminOnly
                ? "Quản trị viên không được phép xem nội dung chi tiết bệnh án!"
                : "Bạn không có quyền truy cập hồ sơ bệnh án này!");
    }

    // ========== Helpers ==========

    private MedicalRecordDTO toDTO(MedicalRecord record) {
        return new MedicalRecordDTO(
                record.getId(),
                record.getRecordCode(),
                record.getPatientId(),
                record.getPatient() != null ? record.getPatient().getFullName() : null,
                record.getBloodType(),
                record.getChronicConditions(),
                record.getAllergyNotes(),
                record.getMedicalHistory(),
                record.getCurrentMedications(),
                record.getStatus(),
                record.getUpdatedAt());
    }
}
