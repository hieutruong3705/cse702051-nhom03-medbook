package com.phenikaa.cse702051.medbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.catalog.MedicineDTO;
import com.phenikaa.cse702051.medbook.service.MedicineService;

/**
 * Tra cứu thuốc trong danh mục để kê đơn. Chỉ bác sĩ và Admin (xem {@code SecurityConfig}); bác sĩ chỉ thấy thuốc
 * đang hoạt động, tham số {@code status} chỉ có tác dụng với Admin.
 */
@RestController
@RequestMapping("/api/v1/medicines")
public class MedicineController {

    private final MedicineService medicineService;

    public MedicineController(MedicineService medicineService) {
        this.medicineService = medicineService;
    }

    @GetMapping
    public PageResponse<MedicineDTO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return medicineService.list(keyword, status, page, size);
    }
}
