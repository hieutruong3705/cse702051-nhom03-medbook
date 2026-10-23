package com.phenikaa.cse702051.medbook.service;

import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.model.Encounter;

/**
 * @deprecated thay bằng {@link EncounterAccessPolicy}. Lớp này chỉ còn là lớp bọc mỏng để
 *             {@code PrescriptionService}/{@code InvoiceService} vẫn biên dịch được; mọi quy tắc
 *             nằm ở {@code EncounterAccessPolicy}. Cần chuyển hai service đó sang {@code EncounterAccessPolicy}
 *             rồi xóa lớp này.
 */
@Deprecated
@Service
public class Dev4AuthorizationService {

    private final EncounterAccessPolicy policy;
    private final CurrentActorService currentActorService;

    public Dev4AuthorizationService(EncounterAccessPolicy policy, CurrentActorService currentActorService) {
        this.policy = policy;
        this.currentActorService = currentActorService;
    }

    /** ID hồ sơ bệnh nhân của người đăng nhập; không có → 403. */
    public Long getCurrentPatientId() {
        return currentActorService.requireCurrentPatientId();
    }

    /** ID hồ sơ bác sĩ của người đăng nhập; không có → 403. */
    public Long getCurrentDoctorId() {
        return currentActorService.requireCurrentDoctorId();
    }

    public void assertPatientOwnsEncounter(Encounter encounter) {
        policy.assertPatientCanRead(encounter);
    }

    public void assertDoctorOwnsEncounter(Encounter encounter) {
        policy.assertDoctorOwns(encounter);
    }

    public void assertPatientOwnsMedicalRecord(Long medicalRecordId) {
        policy.assertPatientOwnsMedicalRecord(medicalRecordId);
    }
}
