package com.phenikaa.cse702051.medbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.MedicalRecordDTO;
import com.phenikaa.cse702051.medbook.service.MedicalRecordService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/medical-records")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    public MedicalRecordController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    @GetMapping("/me")
    public MedicalRecordDTO getCurrentPatientMedicalRecord(HttpServletRequest request) {
        return medicalRecordService.getCurrentPatientMedicalRecord(request);
    }

    @GetMapping("/{id}")
    public MedicalRecordDTO getMedicalRecordById(@PathVariable Long id, HttpServletRequest request) {
        return medicalRecordService.getMedicalRecordById(id, request);
    }
}
