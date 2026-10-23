package com.phenikaa.cse702051.medbook.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionPatientDTO;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionPatientRequest;
import com.phenikaa.cse702051.medbook.service.ReceptionPatientService;

import jakarta.validation.Valid;

/**
 * Bệnh nhân tại quầy lễ tân. Chỉ RECEPTIONIST (xem {@code SecurityConfig}: {@code /reception/**}). Chỉ trả thông
 * tin hành chính.
 */
@RestController
@RequestMapping("/api/v1/reception/patients")
public class ReceptionPatientController {

    private final ReceptionPatientService receptionPatientService;

    public ReceptionPatientController(ReceptionPatientService receptionPatientService) {
        this.receptionPatientService = receptionPatientService;
    }

    /** Tìm theo họ tên, mã bệnh nhân hoặc điện thoại; theo họ tên tăng dần. */
    @GetMapping
    public PageResponse<ReceptionPatientDTO> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return receptionPatientService.search(keyword, page, size);
    }

    @GetMapping("/{id:\\d+}")
    public ReceptionPatientDTO get(@PathVariable Long id) {
        return receptionPatientService.get(id);
    }

    /** Tạo hồ sơ cho bệnh nhân chưa có tài khoản. */
    @PostMapping
    public ResponseEntity<ReceptionPatientDTO> create(@Valid @RequestBody ReceptionPatientRequest request) {
        ReceptionPatientDTO patient = receptionPatientService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/reception/patients/" + patient.id())).body(patient);
    }
}
