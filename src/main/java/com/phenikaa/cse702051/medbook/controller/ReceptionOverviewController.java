package com.phenikaa.cse702051.medbook.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.reception.ReceptionOverviewDTO;
import com.phenikaa.cse702051.medbook.service.ReceptionOverviewService;

/** Số liệu trong ngày của quầy lễ tân. Chỉ RECEPTIONIST (xem {@code SecurityConfig}: {@code /reception/**}). */
@RestController
@RequestMapping("/api/v1/reception/overview")
public class ReceptionOverviewController {

    private final ReceptionOverviewService receptionOverviewService;

    public ReceptionOverviewController(ReceptionOverviewService receptionOverviewService) {
        this.receptionOverviewService = receptionOverviewService;
    }

    /** {@code date} tùy chọn, mặc định hôm nay. */
    @GetMapping
    public ReceptionOverviewDTO overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return receptionOverviewService.overview(date);
    }
}
