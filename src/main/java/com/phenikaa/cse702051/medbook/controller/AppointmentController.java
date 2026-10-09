
package com.phenikaa.cse702051.medbook.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import com.phenikaa.cse702051.medbook.service.AuditLogService;

@RestController
@RequestMapping({
    "/api/appointments",
    "/api/v1/appointments"
})
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentReportService reportService;
    private final AuditLogService auditLogService;

    // Dat lich kham: danh tinh benh nhan lay tu Authentication.
    @PostMapping
    public ResponseEntity<Appointment> bookAppointment(
            @RequestParam Long slotId,
            @RequestParam(required = false) String notes,
            Authentication authentication,
            HttpServletRequest request) {

        Appointment appointment = appointmentService.bookAppointment(
                slotId, notes, authentication);

        auditLogService.record(
                "APPOINTMENT_BOOKED",
                "APPOINTMENT",
                appointment.getId(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                "{\"patientId\":" + appointment.getPatientId()
                        + ",\"slotId\":" + appointment.getSlot().getId() + "}");

        return ResponseEntity.status(HttpStatus.CREATED).body(appointment);
    }

    // Benh nhan huy lich cua chinh minh.
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Appointment> cancelAppointment(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(
                appointmentService.cancelAppointment(id, authentication));
    }

    // Benh nhan doi lich cua chinh minh.
    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<Appointment> rescheduleAppointment(
            @PathVariable Long id,
            @RequestParam Long newSlotId,
            Authentication authentication) {

        return ResponseEntity.ok(
                appointmentService.rescheduleAppointment(
                        id, newSlotId, authentication));
    }

    // Bac si phu trach cap nhat trang thai lich.
    @PatchMapping("/{id}/status")
    public ResponseEntity<Appointment> updateStatus(
            @PathVariable Long id,
            @RequestParam AppointmentStatus status,
            Authentication authentication) {

        return ResponseEntity.ok(
                appointmentService.updateStatus(
                        id, status, authentication));
    }

    // Chi nguoi co quyen theo service moi duoc xem chi tiet.
    @GetMapping("/{id}")
    public ResponseEntity<Appointment> getAppointmentById(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(
                appointmentService.getAppointmentById(id, authentication));
    }

    // Benh nhan chi xem danh sach cua chinh minh.
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<Appointment>> getAppointmentsByPatient(
            @PathVariable Long patientId,
            Authentication authentication) {

        return ResponseEntity.ok(
                appointmentService.getAppointmentsByPatient(
                        patientId, authentication));
    }

    // Bao cao thong ke danh cho Admin.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/reports")
    public ResponseEntity<AppointmentStatisticsDTO> getOverallReport() {
        return ResponseEntity.ok(reportService.getOverallStatistics());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/reports/doctor/{doctorId}")
    public ResponseEntity<AppointmentStatisticsDTO> getDoctorReport(
            @PathVariable Long doctorId) {

        return ResponseEntity.ok(
                reportService.getStatisticsByDoctor(doctorId));
    }
}
