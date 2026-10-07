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

import com.phenikaa.cse702051.medbook.dto.AppointmentDTO;
import com.phenikaa.cse702051.medbook.dto.BookAppointmentRequest;
import com.phenikaa.cse702051.medbook.dto.CancelAppointmentRequest;
import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.RescheduleAppointmentRequest;
import com.phenikaa.cse702051.medbook.dto.UpdateAppointmentStatusRequest;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.service.AppointmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Lịch hẹn (YCCN-09…16). Danh tính bệnh nhân/bác sĩ luôn lấy từ JWT — KHÔNG có tham số
 * {@code patientId}/{@code doctorId} nào trong request.
 */
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    /** YCCN-10, 11: bệnh nhân đặt lịch. Slot đã bị người khác đặt → 409. */
    @PostMapping
    public ResponseEntity<AppointmentDTO> book(@Valid @RequestBody BookAppointmentRequest request) {
        AppointmentDTO appointment = appointmentService.book(request.slotId(), request.serviceId(), request.notes());
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + appointment.id())).body(appointment);
    }

    /** Lịch của người đang đăng nhập: bác sĩ → lịch khám của mình; bệnh nhân → lịch của mình. */
    @GetMapping("/me")
    public PageResponse<AppointmentDTO> myAppointments(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return appointmentService.getMyAppointments(status, from, to, order, page, size);
    }

    /** Chi tiết lịch hẹn: chủ lịch hoặc bác sĩ phụ trách. */
    @GetMapping("/{id}")
    public AppointmentDTO getById(@PathVariable Long id) {
        return appointmentService.getById(id);
    }

    /** YCCN-12: bệnh nhân hủy lịch của mình (chỉ khi BOOKED và còn đủ thời hạn). */
    @PatchMapping("/{id}/cancel")
    public AppointmentDTO cancel(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelAppointmentRequest request) {
        return appointmentService.cancel(id, request == null ? null : request.reason());
    }

    /** YCCN-13: bệnh nhân đổi lịch sang slot trống khác, nguyên tử. */
    @PatchMapping("/{id}/reschedule")
    public AppointmentDTO reschedule(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleAppointmentRequest request) {
        return appointmentService.reschedule(id, request.newSlotId(), request.reason());
    }

    /** YCCN-16: bác sĩ phụ trách chuyển BOOKED → IN_PROGRESS → COMPLETED. */
    @PatchMapping("/{id}/status")
    public AppointmentDTO updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentStatusRequest request) {
        return appointmentService.updateStatus(id, request.status());
    }
}
