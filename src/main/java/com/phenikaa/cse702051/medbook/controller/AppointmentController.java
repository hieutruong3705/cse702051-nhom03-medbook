package com.phenikaa.cse702051.medbook.controller;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.service.AppointmentService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<Appointment> findAll() {
        return appointmentService.findAll();
    }

    @GetMapping("/{id}")
    public Appointment findById(@PathVariable Long id) {
        return appointmentService.findById(id).orElse(null);
    }

    @GetMapping("/code/{appointmentCode}")
    public Appointment findByAppointmentCode(
            @PathVariable String appointmentCode
    ) {
        return appointmentService
                .findByAppointmentCode(appointmentCode)
                .orElse(null);
    }

    @GetMapping("/patient/{patientId}")
    public List<Appointment> findByPatientId(
            @PathVariable Long patientId
    ) {
        return appointmentService.findByPatientId(patientId);
    }

    @GetMapping("/doctor/{doctorId}")
    public List<Appointment> findByDoctorId(
            @PathVariable Long doctorId
    ) {
        return appointmentService.findByDoctorId(doctorId);
    }

    @GetMapping("/date/{appointmentDate}")
    public List<Appointment> findByAppointmentDate(
            @PathVariable LocalDate appointmentDate
    ) {
        return appointmentService.findByAppointmentDate(appointmentDate);
    }
}