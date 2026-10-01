package com.phenikaa.cse702051.medbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.MedicalRecordDTO;
import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.PatientDTO;
import com.phenikaa.cse702051.medbook.dto.PatientUpdateRequest;
import com.phenikaa.cse702051.medbook.service.MedicalRecordService;
import com.phenikaa.cse702051.medbook.service.PatientService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;
    private final MedicalRecordService medicalRecordService;

    public PatientController(PatientService patientService, MedicalRecordService medicalRecordService) {
        this.patientService = patientService;
        this.medicalRecordService = medicalRecordService;
    }

    /** Hồ sơ bệnh nhân đang đăng nhập (PATIENT). */
    @GetMapping("/me")
    public PatientDTO getCurrentPatient() {
        return patientService.getCurrentPatient();
    }

    @PutMapping("/me")
    public PatientDTO updateCurrentPatient(@Valid @RequestBody PatientUpdateRequest updateRequest) {
        return patientService.updateCurrentPatient(updateRequest);
    }

    /**
     * Danh sách bệnh nhân: DOCTOR thấy bệnh nhân thuộc phạm vi phụ trách, ADMIN thấy trường hành chính.
     */
    @GetMapping
    public PageResponse<?> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return patientService.search(keyword, page, size);
    }

    /** Chi tiết bệnh nhân: DOCTOR (phụ trách) nhận PatientDTO; ADMIN nhận PatientAdminDTO. */
    @GetMapping("/{id}")
    public Object getById(@PathVariable Long id) {
        return patientService.getById(id);
    }

    /** Bệnh án của bệnh nhân, dành cho bác sĩ phụ trách (ghi audit). */
    @GetMapping("/{id}/medical-records")
    public MedicalRecordDTO getMedicalRecord(@PathVariable Long id) {
        return medicalRecordService.getRecordOfPatient(id);
    }
}
