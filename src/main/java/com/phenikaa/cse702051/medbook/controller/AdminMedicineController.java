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
import com.phenikaa.cse702051.medbook.dto.catalog.MedicineAdminDTO;
import com.phenikaa.cse702051.medbook.dto.catalog.MedicineRequest;
import com.phenikaa.cse702051.medbook.service.MedicineService;

import jakarta.validation.Valid;

/** Quản trị danh mục thuốc (YCCN-20). Chỉ ADMIN (xem {@code SecurityConfig}: {@code /admin/**}). */
@RestController
@RequestMapping("/api/v1/admin/medicines")
public class AdminMedicineController {

    private final MedicineService medicineService;

    public AdminMedicineController(MedicineService medicineService) {
        this.medicineService = medicineService;
    }

    @GetMapping
    public PageResponse<MedicineAdminDTO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return medicineService.listForAdmin(keyword, status, page, size);
    }

    @PostMapping
    public ResponseEntity<MedicineAdminDTO> create(@Valid @RequestBody MedicineRequest request) {
        MedicineAdminDTO created = medicineService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/medicines/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public MedicineAdminDTO get(@PathVariable Long id) {
        return medicineService.getForAdmin(id);
    }

    @PutMapping("/{id}")
    public MedicineAdminDTO update(@PathVariable Long id, @Valid @RequestBody MedicineRequest request) {
        return medicineService.update(id, request);
    }

    /** 204 nếu đã xóa hẳn; 200 kèm bản ghi {@code INACTIVE} nếu thuốc đã được kê trong đơn nào đó. */
    @DeleteMapping("/{id}")
    public ResponseEntity<MedicineAdminDTO> delete(@PathVariable Long id) {
        return medicineService.delete(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
