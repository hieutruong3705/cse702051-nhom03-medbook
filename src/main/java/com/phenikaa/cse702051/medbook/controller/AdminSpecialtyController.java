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
import com.phenikaa.cse702051.medbook.dto.catalog.SpecialtyAdminDTO;
import com.phenikaa.cse702051.medbook.dto.catalog.SpecialtyRequest;
import com.phenikaa.cse702051.medbook.service.SpecialtyService;

import jakarta.validation.Valid;

/** Quản trị danh mục chuyên khoa (YCCN-20). Chỉ ADMIN (xem {@code SecurityConfig}: {@code /admin/**}). */
@RestController
@RequestMapping("/api/v1/admin/specialties")
public class AdminSpecialtyController {

    private final SpecialtyService specialtyService;

    public AdminSpecialtyController(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    @GetMapping
    public PageResponse<SpecialtyAdminDTO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return specialtyService.listForAdmin(keyword, status, page, size);
    }

    @PostMapping
    public ResponseEntity<SpecialtyAdminDTO> create(@Valid @RequestBody SpecialtyRequest request) {
        SpecialtyAdminDTO created = specialtyService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/specialties/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public SpecialtyAdminDTO get(@PathVariable Long id) {
        return specialtyService.getForAdmin(id);
    }

    @PutMapping("/{id}")
    public SpecialtyAdminDTO update(@PathVariable Long id, @Valid @RequestBody SpecialtyRequest request) {
        return specialtyService.update(id, request);
    }

    /** 204 nếu đã xóa hẳn; 200 kèm bản ghi {@code INACTIVE} nếu chuyên khoa đang được hồ sơ bác sĩ dùng. */
    @DeleteMapping("/{id}")
    public ResponseEntity<SpecialtyAdminDTO> delete(@PathVariable Long id) {
        return specialtyService.delete(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
