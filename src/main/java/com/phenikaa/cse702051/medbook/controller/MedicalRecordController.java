package com.phenikaa.cse702051.medbook.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.MedicalRecordDTO;
import com.phenikaa.cse702051.medbook.service.MedicalRecordService;

@RestController
@RequestMapping({
        "/api/v1/medical-records",
        "/api/medical-records"
})
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    public MedicalRecordController(
            MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicalRecordDTO> getById(
            @PathVariable Long id,
            Authentication authentication) {

        MedicalRecordDTO dto =
                medicalRecordService.getRecordForUser(id, authentication);

        return ResponseEntity.ok(dto);
    }
}
