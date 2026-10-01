
package com.phenikaa.cse702051.medbook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.phenikaa.cse702051.medbook.model.Prescription;
import com.phenikaa.cse702051.medbook.service.PrescriptionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping
    public ResponseEntity<Prescription> createPrescription(
            @RequestParam Long encounterId,
            @RequestParam(required = false) String notes) {

        Prescription prescription = prescriptionService.createPrescription(
                encounterId,
                notes
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(prescription);
    }
}