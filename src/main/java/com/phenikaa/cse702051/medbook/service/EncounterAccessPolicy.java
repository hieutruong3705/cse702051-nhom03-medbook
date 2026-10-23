package com.phenikaa.cse702051.medbook.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Quy tắc truy cập dữ liệu khám (lần khám, đơn thuốc, tệp, hóa đơn) — thay cho {@code Dev4AuthorizationService}.
 *
 * <ul>
 * <li><b>Đọc</b>: bệnh nhân chủ bệnh án hoặc bác sĩ phụ trách lần khám. Admin KHÔNG đọc được dữ liệu lâm sàng.</li>
 * <li><b>Ghi</b>: chỉ bác sĩ phụ trách lần khám.</li>
 * <li>Danh tính luôn lấy từ JWT qua {@link CurrentUserService}; không nhận ID từ client.</li>
 * <li>Truy cập tài nguyên của người khác trả <b>403</b> nhất quán (kèm audit {@code ACCESS_DENIED}); ID không
 * tồn tại trả 404.</li>
 * </ul>
 */
@Service
public class EncounterAccessPolicy {

    private final CurrentUserService currentUserService;
    private final CurrentActorService currentActorService;
    private final MedicalRecordService medicalRecordService;
    private final AuditLogService auditLogService;

    public EncounterAccessPolicy(
            CurrentUserService currentUserService,
            CurrentActorService currentActorService,
            MedicalRecordService medicalRecordService,
            AuditLogService auditLogService) {
        this.currentUserService = currentUserService;
        this.currentActorService = currentActorService;
        this.medicalRecordService = medicalRecordService;
        this.auditLogService = auditLogService;
    }

    /** ID hồ sơ bệnh nhân của người đăng nhập (phải có role PATIENT), không có → 403. */
    public Long currentPatientId() {
        currentUserService.requireRole("PATIENT");
        return currentActorService.requireCurrentPatientId();
    }

    /** ID hồ sơ bác sĩ của người đăng nhập (phải có role DOCTOR), không có → 403. */
    public Long currentDoctorId() {
        currentUserService.requireRole("DOCTOR");
        return currentActorService.requireCurrentDoctorId();
    }

    /** Bệnh nhân hiện tại phải là chủ của bệnh án chứa lần khám này. */
    @Transactional(readOnly = true)
    public void assertPatientCanRead(Encounter encounter) {
        CurrentUser user = currentUserService.requireCurrentUser();
        if (!isOwnerPatient(user, encounter)) {
            deny(encounter.getId(), user.hasRole("ADMIN") && !user.hasRole("PATIENT")
                    ? "Quản trị viên không được đọc dữ liệu khám bệnh"
                    : "Không phải chủ sở hữu lần khám");
        }
    }

    /** Bác sĩ hiện tại phải là bác sĩ phụ trách lần khám; người khác (kể cả bệnh nhân, Admin) → 403. */
    @Transactional(readOnly = true)
    public void assertDoctorOwns(Encounter encounter) {
        CurrentUser user = currentUserService.requireCurrentUser();
        if (!isOwnerDoctor(user, encounter)) {
            deny(encounter.getId(), user.hasRole("DOCTOR")
                    ? "Bác sĩ không phụ trách lần khám này"
                    : "Chỉ bác sĩ phụ trách được thao tác trên lần khám");
        }
    }

    /** Đọc được khi là bệnh nhân chủ hoặc bác sĩ phụ trách. */
    @Transactional(readOnly = true)
    public void assertCanRead(Encounter encounter) {
        CurrentUser user = currentUserService.requireCurrentUser();
        if (isOwnerPatient(user, encounter) || isOwnerDoctor(user, encounter)) {
            return;
        }
        boolean adminOnly = user.hasRole("ADMIN") && !user.hasRole("DOCTOR") && !user.hasRole("PATIENT");
        deny(encounter.getId(), adminOnly
                ? "Quản trị viên không được đọc dữ liệu khám bệnh"
                : "Không phải bệnh nhân chủ hoặc bác sĩ phụ trách");
    }

    /** Bệnh nhân hiện tại phải là chủ của bệnh án {@code medicalRecordId}. */
    @Transactional(readOnly = true)
    public void assertPatientOwnsMedicalRecord(Long medicalRecordId) {
        currentUserService.requireRole("PATIENT");
        MedicalRecord record = medicalRecordService.findById(medicalRecordId);
        Long patientId = currentActorService.requireCurrentPatientId();
        if (!patientId.equals(record.getPatientId())) {
            auditLogService.recordAccessDenied(AuditActions.ENTITY_MEDICAL_RECORDS, medicalRecordId,
                    "Không phải chủ sở hữu bệnh án");
            throw new ForbiddenException("Bạn không có quyền truy cập hồ sơ bệnh án này!");
        }
    }

    /** Ghi audit {@code ACCESS_DENIED} (vẫn lưu dù giao dịch gọi rollback) rồi ném 403. */
    public ForbiddenException deny(String entityType, Long entityId, String reason) {
        auditLogService.recordAccessDenied(entityType, entityId, reason);
        throw new ForbiddenException("Bạn không có quyền truy cập tài nguyên này!");
    }

    private void deny(Long encounterId, String reason) {
        deny(AuditActions.ENTITY_ENCOUNTERS, encounterId, reason);
    }

    private boolean isOwnerPatient(CurrentUser user, Encounter encounter) {
        if (!user.hasRole("PATIENT")) {
            return false;
        }
        MedicalRecord record = medicalRecordService.findById(encounter.getMedicalRecordId());
        return currentActorService.findCurrentPatientId()
                .map(patientId -> patientId.equals(record.getPatientId()))
                .orElse(false);
    }

    private boolean isOwnerDoctor(CurrentUser user, Encounter encounter) {
        return user.hasRole("DOCTOR")
                && currentActorService.findCurrentDoctorId()
                        .map(doctorId -> doctorId.equals(encounter.getDoctorId()))
                        .orElse(false);
    }
}
