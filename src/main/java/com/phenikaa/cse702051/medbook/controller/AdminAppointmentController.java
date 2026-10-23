package com.phenikaa.cse702051.medbook.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.report.AdminAppointmentDTO;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.service.AdminAppointmentService;

/**
 * Admin theo dõi lịch hẹn (YCCN-22), chỉ cột hành chính. Chỉ ADMIN (xem {@code SecurityConfig}: {@code /admin/**}).
 */
@RestController
@RequestMapping("/api/v1/admin/appointments")
public class AdminAppointmentController {

    private final AdminAppointmentService adminAppointmentService;

    public AdminAppointmentController(AdminAppointmentService adminAppointmentService) {
        this.adminAppointmentService = adminAppointmentService;
    }

    /** Lọc theo ngày khám, bác sĩ, chuyên khoa, trạng thái; mới nhất trước. */
    @GetMapping
    public PageResponse<AdminAppointmentDTO> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminAppointmentService.list(from, to, doctorId, specialtyId, status, page, size);
    }
}
