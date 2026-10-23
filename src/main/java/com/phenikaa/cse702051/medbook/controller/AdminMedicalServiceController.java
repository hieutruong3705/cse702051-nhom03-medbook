package com.phenikaa.cse702051.medbook.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.catalog.MedicalServiceAdminDTO;
import com.phenikaa.cse702051.medbook.dto.catalog.MedicalServiceRequest;
import com.phenikaa.cse702051.medbook.service.MedicalServiceService;

import jakarta.validation.Valid;

/** Quản trị danh mục dịch vụ khám (YCCN-20). Chỉ ADMIN (xem {@code SecurityConfig}: {@code /admin/**}). */
@RestController
@RequestMapping("/api/v1/admin/medical-services")
public class AdminMedicalServiceController {

    private final MedicalServiceService medicalServiceService;

    public AdminMedicalServiceController(MedicalServiceService medicalServiceService) {
        this.medicalServiceService = medicalServiceService;
    }

    @GetMapping
    public PageResponse<MedicalServiceAdminDTO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return medicalServiceService.listForAdmin(keyword, status, page, size);
    }

    @PostMapping
    public ResponseEntity<MedicalServiceAdminDTO> create(@Valid @RequestBody MedicalServiceRequest request) {
        MedicalServiceAdminDTO created = medicalServiceService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/medical-services/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public MedicalServiceAdminDTO get(@PathVariable Long id) {
        return medicalServiceService.getForAdmin(id);
    }

    @PutMapping("/{id}")
    public MedicalServiceAdminDTO update(@PathVariable Long id, @Valid @RequestBody MedicalServiceRequest request) {
        return medicalServiceService.update(id, request);
    }

    /** 204 nếu đã xóa hẳn; 200 kèm bản ghi {@code INACTIVE} nếu dịch vụ đã có lịch hẹn hoặc hóa đơn dùng. */
    @DeleteMapping("/{id}")
    public ResponseEntity<MedicalServiceAdminDTO> delete(@PathVariable Long id) {
        return medicalServiceService.delete(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
