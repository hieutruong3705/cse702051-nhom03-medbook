package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.Prescription;
import com.phenikaa.cse702051.medbook.repository.PrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final EncounterService encounterService;
    private final Dev4AuthorizationService authorizationService;

    public PrescriptionService(
            PrescriptionRepository prescriptionRepository,
            EncounterService encounterService,
            Dev4AuthorizationService authorizationService
    ) {
        this.prescriptionRepository = prescriptionRepository;
        this.encounterService = encounterService;
        this.authorizationService = authorizationService;
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Transactional
    public Prescription create(
            Long encounterId,
            Prescription prescription
    ) {
        if (encounterId == null || encounterId <= 0) {
            throw new IllegalArgumentException(
                    "Encounter ID không hợp lệ"
            );
        }

        if (prescription == null) {
            throw new IllegalArgumentException(
                    "Prescription không được null"
            );
        }

        /*
         * Chỉ bác sĩ phụ trách encounter mới được kê đơn.
         */
        Encounter encounter =
                encounterService.getAccessibleById(encounterId);

        authorizationService.assertDoctorOwnsEncounter(encounter);

        /*
         * Không cho client tự thay đổi encounter.
         */
        prescription.setEncounterId(encounterId);

        /*
         * Tự sinh prescription code nếu request không truyền.
         */
        if (prescription.getPrescriptionCode() == null
                || prescription.getPrescriptionCode().isBlank()) {

            prescription.setPrescriptionCode(
                    generateUniquePrescriptionCode()
            );
        } else {

            if (prescriptionRepository.existsByPrescriptionCode(
                    prescription.getPrescriptionCode()
            )) {
                throw new IllegalArgumentException(
                        "Prescription code đã tồn tại"
                );
            }
        }

        if (prescription.getIssuedAt() == null) {
            prescription.setIssuedAt(LocalDateTime.now());
        }

        if (prescription.getStatus() == null
                || prescription.getStatus().isBlank()) {
            prescription.setStatus("ACTIVE");
        }

        validateStatus(prescription.getStatus());

        LocalDateTime now = LocalDateTime.now();

        prescription.setCreatedAt(now);
        prescription.setUpdatedAt(now);

        return prescriptionRepository.save(prescription);
    }

    // ============================================================
    // GET BY ID
    // ============================================================

    @Transactional(readOnly = true)
    public Prescription getById(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Prescription ID không hợp lệ"
            );
        }

        Prescription prescription =
                prescriptionRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Không tìm thấy prescription với ID: "
                                                + id
                                )
                        );

        /*
         * EncounterService tự kiểm tra:
         * - patient chỉ xem encounter của mình
         * - doctor chỉ xem encounter mình phụ trách
         */
        encounterService.getAccessibleById(
                prescription.getEncounterId()
        );

        return prescription;
    }

    // ============================================================
    // GET BY ENCOUNTER
    // ============================================================

    @Transactional(readOnly = true)
    public List<Prescription> getByEncounterId(
            Long encounterId
    ) {

        if (encounterId == null || encounterId <= 0) {
            throw new IllegalArgumentException(
                    "Encounter ID không hợp lệ"
            );
        }

        /*
         * Kiểm tra quyền trước khi trả danh sách prescription.
         */
        encounterService.getAccessibleById(encounterId);

        return prescriptionRepository.findByEncounterId(
                encounterId
        );
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @Transactional
    public Prescription update(
            Long id,
            Prescription request
    ) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Prescription ID không hợp lệ"
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Prescription không được null"
            );
        }

        Prescription existing =
                prescriptionRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Không tìm thấy prescription với ID: "
                                                + id
                                )
                        );

        Encounter encounter =
                encounterService.getAccessibleById(
                        existing.getEncounterId()
                );

        /*
         * Chỉ bác sĩ phụ trách encounter được sửa.
         */
        authorizationService.assertDoctorOwnsEncounter(
                encounter
        );

        /*
         * Không cho sửa encounterId hoặc prescriptionCode.
         */
        if (request.getNotes() != null) {
            existing.setNotes(request.getNotes());
        }

        if (request.getIssuedAt() != null) {
            existing.setIssuedAt(request.getIssuedAt());
        }

        if (request.getStatus() != null
                && !request.getStatus().isBlank()) {

            validateStatus(request.getStatus());

            existing.setStatus(request.getStatus());
        }

        existing.setUpdatedAt(LocalDateTime.now());

        return prescriptionRepository.save(existing);
    }

    // ============================================================
    // GET BY STATUS
    // ============================================================

    @Transactional(readOnly = true)
    public List<Prescription> getByEncounterIdAndStatus(
            Long encounterId,
            String status
    ) {

        if (encounterId == null || encounterId <= 0) {
            throw new IllegalArgumentException(
                    "Encounter ID không hợp lệ"
            );
        }

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Status không được để trống"
            );
        }

        encounterService.getAccessibleById(encounterId);

        validateStatus(status);

        return prescriptionRepository
                .findByEncounterIdAndStatus(
                        encounterId,
                        status
                );
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private void validateStatus(String status) {

        if (!"ACTIVE".equalsIgnoreCase(status)
                && !"CANCELLED".equalsIgnoreCase(status)) {

            throw new IllegalArgumentException(
                    "Status prescription chỉ được ACTIVE hoặc CANCELLED"
            );
        }
    }

    // ============================================================
    // GENERATE CODE
    // ============================================================

    private String generateUniquePrescriptionCode() {

        String code;

        do {
            code = "RX-"
                    + UUID.randomUUID()
                    .toString()
                    .substring(0, 8)
                    .toUpperCase();

        } while (prescriptionRepository
                .existsByPrescriptionCode(code));

        return code;
    }
}