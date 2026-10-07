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

import com.phenikaa.cse702051.medbook.dto.report.AppointmentReportDTO;
import com.phenikaa.cse702051.medbook.dto.report.CsvExport;
import com.phenikaa.cse702051.medbook.service.AppointmentReportService;

/**
 * Báo cáo lịch khám cho Admin (YCCN-22). Chỉ ADMIN (xem {@code SecurityConfig}: {@code /admin/**}). Không truyền
 * ngày thì lấy 30 ngày gần nhất; khoảng ngày tối đa 366 ngày.
 */
@RestController
@RequestMapping("/api/v1/admin/reports/appointments")
public class AdminAppointmentReportController {

    private static final MediaType CSV_UTF8 = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final AppointmentReportService appointmentReportService;

    public AdminAppointmentReportController(AppointmentReportService appointmentReportService) {
        this.appointmentReportService = appointmentReportService;
    }

    /** {@code groupBy}: NONE (mặc định), DAY, DOCTOR hoặc SPECIALTY. */
    @GetMapping
    public AppointmentReportDTO report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) String groupBy) {
        return appointmentReportService.report(from, to, doctorId, specialtyId, groupBy);
    }

    /** Tải tệp CSV danh sách lịch hẹn khớp bộ lọc. Vượt giới hạn số dòng → 422. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(defaultValue = "csv") String format) {
        CsvExport export = appointmentReportService.exportCsv(from, to, doctorId, specialtyId, format);
        return ResponseEntity.ok()
                .contentType(CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.content());
    }
}
