package com.phenikaa.cse702051.medbook.controller;

import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.service.EncounterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/encounters")
public class EncounterController {

    private final EncounterService encounterService;

    public EncounterController(
            EncounterService encounterService
    ) {
        this.encounterService = encounterService;
    }

    /**
     * Tạo encounter.
     *
     * POST /api/encounters
     */
    @PostMapping
    public ResponseEntity<Encounter> create(
            @RequestBody Encounter encounter
    ) {
        return ResponseEntity.ok(
                encounterService.create(encounter)
        );
    }

    /**
     * Xem encounter theo ID.
     *
     * Patient: chỉ xem encounter của mình.
     * Doctor: chỉ xem encounter mình phụ trách.
     *
     * GET /api/encounters/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Encounter> getById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                encounterService.getAccessibleById(id)
        );
    }

    /**
     * Lấy encounter theo medical record.
     *
     * GET /api/encounters/medical-record/{medicalRecordId}
     */
    @GetMapping("/medical-record/{medicalRecordId}")
    public ResponseEntity<List<Encounter>> getByMedicalRecordId(
            @PathVariable Long medicalRecordId
    ) {
        return ResponseEntity.ok(
                encounterService.getByMedicalRecordId(
                        medicalRecordId
                )
        );
    }

    /**
     * Lấy encounter theo doctor.
     *
     * Chỉ doctor đang đăng nhập mới được xem
     * encounter của chính mình.
     *
     * GET /api/encounters/doctor/{doctorId}
     */
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<Encounter>> getByDoctorId(
            @PathVariable Long doctorId
    ) {
        return ResponseEntity.ok(
                encounterService.getByDoctorId(doctorId)
        );
    }

    /**
     * Cập nhật encounter.
     *
     * Chỉ doctor phụ trách encounter mới được sửa.
     *
     * PUT /api/encounters/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Encounter> update(
            @PathVariable Long id,
            @RequestBody Encounter encounter
    ) {
        return ResponseEntity.ok(
                encounterService.update(
                        id,
                        encounter
                )
        );
    }
}