package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.Prescription;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.repository.PrescriptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final EncounterRepository encounterRepository;

    public Prescription createPrescription(
            Long encounterId,
            String notes) {

        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy lượt khám"));

        LocalDateTime now = LocalDateTime.now();

        Prescription prescription = new Prescription();
        prescription.setEncounter(encounter);
        prescription.setPrescriptionCode("RX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        prescription.setIssuedAt(now);
        prescription.setNotes(notes);
        prescription.setStatus("ISSUED");
        prescription.setCreatedAt(now);
        prescription.setUpdatedAt(now);

        return prescriptionRepository.save(prescription);
    }
}