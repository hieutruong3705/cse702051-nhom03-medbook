package com.phenikaa.cse702051.medbook.controller;

import java.net.URI;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionAppointmentDTO;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionBookRequest;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionCancelRequest;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionRescheduleRequest;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.service.ReceptionAppointmentService;

import jakarta.validation.Valid;

/**
 * Lịch hẹn tại quầy lễ tân: xem lịch toàn phòng khám, đặt, hủy, đổi lịch hộ bệnh nhân. Chỉ RECEPTIONIST (xem
 * {@code SecurityConfig}: {@code /reception/**}).
 */
@RestController
@RequestMapping("/api/v1/reception/appointments")
public class ReceptionAppointmentController {

    private final ReceptionAppointmentService receptionAppointmentService;

    public ReceptionAppointmentController(ReceptionAppointmentService receptionAppointmentService) {
        this.receptionAppointmentService = receptionAppointmentService;
    }

    /** Lọc theo ngày khám (mặc định hôm nay), bác sĩ, trạng thái, từ khóa bệnh nhân; sớm nhất trước. */
    @GetMapping
    public PageResponse<ReceptionAppointmentDTO> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return receptionAppointmentService.list(from, to, doctorId, status, keyword, page, size);
    }

    @GetMapping("/{id:\\d+}")
    public ReceptionAppointmentDTO get(@PathVariable Long id) {
        return receptionAppointmentService.get(id);
    }

    /** Đặt lịch hộ một bệnh nhân. Khung giờ đã có người đặt → 409. */
    @PostMapping
    public ResponseEntity<ReceptionAppointmentDTO> book(@Valid @RequestBody ReceptionBookRequest request) {
        ReceptionAppointmentDTO appointment = receptionAppointmentService.book(request);
        return ResponseEntity.created(URI.create("/api/v1/reception/appointments/" + appointment.id()))
                .body(appointment);
    }

    /** Hủy lịch hộ; bắt buộc có lý do. */
    @PatchMapping("/{id:\\d+}/cancel")
    public ReceptionAppointmentDTO cancel(@PathVariable Long id,
            @Valid @RequestBody ReceptionCancelRequest request) {
        return receptionAppointmentService.cancel(id, request.reason());
    }

    /** Đổi lịch hộ sang khung giờ trống khác; bắt buộc có lý do. */
    @PatchMapping("/{id:\\d+}/reschedule")
    public ReceptionAppointmentDTO reschedule(@PathVariable Long id,
            @Valid @RequestBody ReceptionRescheduleRequest request) {
        return receptionAppointmentService.reschedule(id, request.newSlotId(), request.reason());
    }
}
