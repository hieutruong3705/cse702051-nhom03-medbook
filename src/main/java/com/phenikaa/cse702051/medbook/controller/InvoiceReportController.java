package com.phenikaa.cse702051.medbook.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.invoice.RevenueReportDTO;
import com.phenikaa.cse702051.medbook.dto.invoice.ServiceRevenueReportDTO;
import com.phenikaa.cse702051.medbook.dto.report.CsvExport;
import com.phenikaa.cse702051.medbook.service.InvoiceReportService;

/**
 * Báo cáo doanh thu và báo cáo dịch vụ khám cho Admin (YCCN-22). Chỉ ADMIN (xem {@code SecurityConfig}:
 * {@code /admin/**}). Không truyền ngày thì lấy 30 ngày gần nhất; khoảng ngày tối đa 366 ngày. Mỗi báo cáo có
 * thêm đường dẫn {@code /export} tải tệp CSV mở được bằng Excel.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class InvoiceReportController {

    private static final MediaType CSV_UTF8 = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final InvoiceReportService invoiceReportService;

    public InvoiceReportController(InvoiceReportService invoiceReportService) {
        this.invoiceReportService = invoiceReportService;
    }

    /** {@code groupBy}: NONE (mặc định), DAY hoặc MONTH theo ngày lập hóa đơn. */
    @GetMapping("/revenue")
    public RevenueReportDTO revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String groupBy) {
        return invoiceReportService.revenue(from, to, groupBy);
    }

    /** Tải tệp CSV doanh thu theo ngày (mặc định) hoặc theo tháng. */
    @GetMapping("/revenue/export")
    public ResponseEntity<byte[]> exportRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String groupBy) {
        return csv(invoiceReportService.exportRevenueCsv(from, to, groupBy));
    }

    /** Số lần dùng và giá trị của từng dịch vụ khám trong kỳ, giá trị lớn nhất trước. */
    @GetMapping("/services")
    public ServiceRevenueReportDTO services(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return invoiceReportService.services(from, to);
    }

    @GetMapping("/services/export")
    public ResponseEntity<byte[]> exportServices(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return csv(invoiceReportService.exportServicesCsv(from, to));
    }

    private static ResponseEntity<byte[]> csv(CsvExport export) {
        return ResponseEntity.ok()
                .contentType(CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.content());
    }
}
