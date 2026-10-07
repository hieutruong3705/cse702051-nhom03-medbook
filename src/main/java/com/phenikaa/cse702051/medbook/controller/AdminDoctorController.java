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
import com.phenikaa.cse702051.medbook.dto.doctor.AdminDoctorRequest;
import com.phenikaa.cse702051.medbook.dto.doctor.DoctorAdminDTO;
import com.phenikaa.cse702051.medbook.service.DoctorService;

import jakarta.validation.Valid;

/** Quản trị hồ sơ bác sĩ (YCCN-20). Chỉ ADMIN (xem {@code SecurityConfig}: {@code /admin/**}). */
@RestController
@RequestMapping("/api/v1/admin/doctors")
public class AdminDoctorController {

    private final DoctorService doctorService;

    public AdminDoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    /** Tìm theo tên, chuyên khoa hoặc số giấy phép; {@code isActive} bỏ trống là lấy cả hai trạng thái. */
    @GetMapping
    public PageResponse<DoctorAdminDTO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return doctorService.searchForAdmin(keyword, specialtyId, isActive, page, size);
    }

    @PostMapping
    public ResponseEntity<DoctorAdminDTO> create(@Valid @RequestBody AdminDoctorRequest request) {
        DoctorAdminDTO created = doctorService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/doctors/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public DoctorAdminDTO get(@PathVariable Long id) {
        return doctorService.getForAdmin(id);
    }

    @PutMapping("/{id}")
    public DoctorAdminDTO update(@PathVariable Long id, @Valid @RequestBody AdminDoctorRequest request) {
        return doctorService.update(id, request);
    }

    /** 204 nếu đã xóa hẳn; 200 kèm hồ sơ {@code isActive = false} nếu bác sĩ đã có lịch sử. */
    @DeleteMapping("/{id}")
    public ResponseEntity<DoctorAdminDTO> delete(@PathVariable Long id) {
        return doctorService.delete(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
