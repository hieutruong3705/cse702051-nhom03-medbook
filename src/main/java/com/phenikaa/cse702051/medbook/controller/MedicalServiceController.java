package com.phenikaa.cse702051.medbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.catalog.MedicalServiceDTO;
import com.phenikaa.cse702051.medbook.service.MedicalServiceService;

/** Dịch vụ khám cho trang công khai và bước chọn dịch vụ khi đặt lịch: chỉ trả dịch vụ đang hoạt động. */
@RestController
@RequestMapping("/api/v1/medical-services")
public class MedicalServiceController {

    private final MedicalServiceService medicalServiceService;

    public MedicalServiceController(MedicalServiceService medicalServiceService) {
        this.medicalServiceService = medicalServiceService;
    }

    @GetMapping
    public PageResponse<MedicalServiceDTO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return medicalServiceService.listActive(keyword, page, size);
    }

    @GetMapping("/{id}")
    public MedicalServiceDTO get(@PathVariable Long id) {
        return medicalServiceService.getActive(id);
    }
}
