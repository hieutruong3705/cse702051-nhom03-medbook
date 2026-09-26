package com.phenikaa.cse702051.medbook.controller;

import com.phenikaa.cse702051.medbook.service.InvoiceReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
public class InvoiceReportController {

    private final InvoiceReportService invoiceReportService;

    public InvoiceReportController(
            InvoiceReportService invoiceReportService
    ) {
        this.invoiceReportService = invoiceReportService;
    }

    /**
     * Báo cáo doanh thu tổng hợp.
     *
     * GET /api/admin/reports/revenue
     *
     * Có thể truyền:
     * ?from=2026-01-01T00:00:00
     * &to=2026-12-31T23:59:59
     */
    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> getRevenueReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to
    ) {

        Map<String, Object> report;

        if (from == null && to == null) {
            report = invoiceReportService.getRevenueReport();
        } else {
            report = invoiceReportService.getRevenueReport(from, to);
        }

        return ResponseEntity.ok(report);
    }
}