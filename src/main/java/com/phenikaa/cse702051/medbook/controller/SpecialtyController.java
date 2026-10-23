package com.phenikaa.cse702051.medbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.catalog.SpecialtyDTO;
import com.phenikaa.cse702051.medbook.service.SpecialtyService;

/** Chuyên khoa cho trang công khai (YCCN-25): không cần đăng nhập, chỉ trả chuyên khoa đang hoạt động. */
@RestController
@RequestMapping("/api/v1/specialties")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    public SpecialtyController(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    @GetMapping
    public PageResponse<SpecialtyDTO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return specialtyService.listActive(keyword, page, size);
    }

    @GetMapping("/{id}")
    public SpecialtyDTO get(@PathVariable Long id) {
        return specialtyService.getActive(id);
    }
}
