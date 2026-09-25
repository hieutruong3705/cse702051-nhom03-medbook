package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    public List<Appointment> findAll() {
        return appointmentRepository.findAll();
    }

    public Optional<Appointment> findById(Long id) {
        return appointmentRepository.findById(id);
    }

    public Optional<Appointment> findByAppointmentCode(String appointmentCode) {
        return appointmentRepository.findByAppointmentCode(appointmentCode);
    }

    public List<Appointment> findByPatientId(Long patientId) {
        return appointmentRepository.findByPatientId(patientId);
    }

    public List<Appointment> findByDoctorId(Long doctorId) {
        return appointmentRepository.findByDoctorId(doctorId);
    }

    public List<Appointment> findByAppointmentDate(LocalDate appointmentDate) {
        return appointmentRepository.findByAppointmentDate(appointmentDate);
    }

    public List<Appointment> findByPatientIdAndAppointmentDate(
            Long patientId,
            LocalDate appointmentDate
    ) {
        return appointmentRepository.findByPatientIdAndAppointmentDate(
                patientId,
                appointmentDate
        );
    }

    public List<Appointment> findByDoctorIdAndAppointmentDate(
            Long doctorId,
            LocalDate appointmentDate
    ) {
        return appointmentRepository.findByDoctorIdAndAppointmentDate(
                doctorId,
                appointmentDate
        );
    }

    public Appointment save(Appointment appointment) {
        return appointmentRepository.save(appointment);
    }

    public void deleteById(Long id) {
        appointmentRepository.deleteById(id);
    }
}