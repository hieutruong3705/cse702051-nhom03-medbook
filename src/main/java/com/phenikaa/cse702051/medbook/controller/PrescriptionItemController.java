package com.phenikaa.cse702051.medbook.controller;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.phenikaa.cse702051.medbook.model.Prescription;
import com.phenikaa.cse702051.medbook.model.PrescriptionItem;
import com.phenikaa.cse702051.medbook.repository.PrescriptionItemRepository;
import com.phenikaa.cse702051.medbook.repository.PrescriptionRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/prescription-items")
@RequiredArgsConstructor
public class PrescriptionItemController {

    private final PrescriptionItemRepository prescriptionItemRepository;
    private final PrescriptionRepository prescriptionRepository;

    @PostMapping
    public ResponseEntity<PrescriptionItem> createPrescriptionItem(
            @RequestParam Long prescriptionId,
            @RequestParam String medicineName,
            @RequestParam(required = false) String dosage,
            @RequestParam(required = false) String frequency,
            @RequestParam(required = false) Integer durationDays,
            @RequestParam BigDecimal quantity,
            @RequestParam(required = false) String instructions) {

        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn thuốc"));

        PrescriptionItem item = new PrescriptionItem();
        item.setPrescription(prescription);
        item.setMedicineName(medicineName);
        item.setDosage(dosage);
        item.setFrequency(frequency);
        item.setDurationDays(durationDays);
        item.setQuantity(quantity);
        item.setInstructions(instructions);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(prescriptionItemRepository.save(item));
    }
}