package com.phenikaa.cse702051.medbook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.service.EncounterService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/encounters")
@RequiredArgsConstructor
public class EncounterController {

    private final EncounterService encounterService;

    @PostMapping
    public ResponseEntity<Encounter> createEncounter(
            @RequestParam Long medicalRecordId,
            @RequestParam Long appointmentId,
            @RequestParam Long doctorId,
            @RequestParam String chiefComplaint,
            @RequestParam String diagnosis,
            @RequestParam String clinicalNotes,
            @RequestParam String treatmentPlan,
            @RequestParam(required = false) String followUpNote) {

        Encounter encounter = encounterService.createEncounter(
                medicalRecordId,
                appointmentId,
                doctorId,
                chiefComplaint,
                diagnosis,
                clinicalNotes,
                treatmentPlan,
                followUpNote
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(encounter);
    }
}