package com.phenikaa.cse702051.medbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PatientDTO;
import com.phenikaa.cse702051.medbook.dto.PatientUpdateRequest;
import com.phenikaa.cse702051.medbook.service.PatientService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping("/me")
    public PatientDTO getCurrentPatient(HttpServletRequest request) {
        return patientService.getCurrentPatient(request);
    }

    @PutMapping("/me")
    public PatientDTO updateCurrentPatient(
            @RequestBody PatientUpdateRequest updateRequest,
            HttpServletRequest request) {
        return patientService.updateCurrentPatient(updateRequest, request);
    }
}
