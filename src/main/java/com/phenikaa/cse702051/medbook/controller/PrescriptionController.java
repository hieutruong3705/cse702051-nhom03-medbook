package com.phenikaa.cse702051.medbook.controller;

import com.phenikaa.cse702051.medbook.model.Prescription;
import com.phenikaa.cse702051.medbook.service.PrescriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    // Tạo đơn thuốc cho một encounter
    // POST /api/encounters/{id}/prescriptions
    @PostMapping("/encounters/{id}/prescriptions")
    public ResponseEntity<Prescription> createPrescription(
            @PathVariable Long id,
            @RequestBody Prescription prescription
    ) {
        return ResponseEntity.ok(
                prescriptionService.create(id, prescription)
        );
    }

    // Xem danh sách đơn thuốc của encounter
    // GET /api/encounters/{id}/prescriptions
    @GetMapping("/encounters/{id}/prescriptions")
    public ResponseEntity<List<Prescription>> getPrescriptionsByEncounter(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                prescriptionService.getByEncounterId(id)
        );
    }

    // Sửa đơn thuốc
    // PUT /api/prescriptions/{id}
    @PutMapping("/prescriptions/{id}")
    public ResponseEntity<Prescription> updatePrescription(
            @PathVariable Long id,
            @RequestBody Prescription prescription
    ) {
        return ResponseEntity.ok(
                prescriptionService.update(id, prescription)
        );
    }

    // Xem một đơn thuốc
    // GET /api/prescriptions/{id}
    @GetMapping("/prescriptions/{id}")
    public ResponseEntity<Prescription> getPrescription(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                prescriptionService.getById(id)
        );
    }
}