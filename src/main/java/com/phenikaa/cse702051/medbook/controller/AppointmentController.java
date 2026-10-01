package com.phenikaa.cse702051.medbook.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.AppointmentStatisticsDTO;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.service.AppointmentReportService;
import com.phenikaa.cse702051.medbook.service.AppointmentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentReportService reportService;

    // YCCN 10, 11: Đặt lịch khám
    @PostMapping
    public ResponseEntity<Appointment> bookAppointment(
            @RequestParam Long patientId,
            @RequestParam Long slotId,
            @RequestParam(required = false) String notes) {
        Appointment appointment = appointmentService.bookAppointment(patientId, slotId, notes);
        return ResponseEntity.status(HttpStatus.CREATED).body(appointment);
    }

    // YCCN 12: Bệnh nhân hủy lịch
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Appointment> cancelAppointment(
            @PathVariable Long id,
            @RequestParam Long patientId) {
        return ResponseEntity.ok(appointmentService.cancelAppointment(id, patientId));
    }

    // YCCN 13: Bệnh nhân đổi lịch
    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<Appointment> rescheduleAppointment(
            @PathVariable Long id,
            @RequestParam Long newSlotId,
            @RequestParam Long patientId) {
        return ResponseEntity.ok(appointmentService.rescheduleAppointment(id, newSlotId, patientId));
    }

    // YCCN 16: Bác sĩ cập nhật trạng thái lịch khám
    @PatchMapping("/{id}/status")
    public ResponseEntity<Appointment> updateStatus(
            @PathVariable Long id,
            @RequestParam AppointmentStatus status,
            @RequestParam Long doctorId) {
        return ResponseEntity.ok(appointmentService.updateStatus(id, status, doctorId));
    }

    // Lấy chi tiết lịch hẹn
    @GetMapping("/{id}")
    public ResponseEntity<Appointment> getAppointmentById(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.getAppointmentById(id));
    }

    // Lấy lịch hẹn của bệnh nhân
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<Appointment>> getAppointmentsByPatient(@PathVariable Long patientId) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByPatient(patientId));
    }

    // YCCN 22: Báo cáo thống kê tổng thể cho Admin
    @GetMapping("/admin/reports")
    public ResponseEntity<AppointmentStatisticsDTO> getOverallReport() {
        return ResponseEntity.ok(reportService.getOverallStatistics());
    }

    // YCCN 22: Báo cáo thống kê theo bác sĩ
    @GetMapping("/admin/reports/doctor/{doctorId}")
    public ResponseEntity<AppointmentStatisticsDTO> getDoctorReport(@PathVariable Long doctorId) {
        return ResponseEntity.ok(reportService.getStatisticsByDoctor(doctorId));
    }
}