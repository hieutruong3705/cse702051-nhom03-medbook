package com.phenikaa.cse702051.medbook.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.dto.AppointmentStatisticsDTO;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppointmentReportService {

    private final AppointmentRepository appointmentRepository;

    public AppointmentStatisticsDTO getStatisticsByDoctor(Long doctorId) {
        List<Appointment> list = appointmentRepository.findByDoctorId(doctorId);
        return calculateStatistics(list);
    }

    public AppointmentStatisticsDTO getOverallStatistics() {
        List<Appointment> list = appointmentRepository.findAll();
        return calculateStatistics(list);
    }

    private AppointmentStatisticsDTO calculateStatistics(List<Appointment> appointments) {
        long total = appointments.size();
        if (total == 0) {
            return AppointmentStatisticsDTO.builder()
                    .totalAppointments(0)
                    .completedAppointments(0)
                    .cancelledAppointments(0)
                    .cancellationRate(0.0)
                    .build();
        }

        long completed = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                .count();

        long cancelled = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CANCELLED)
                .count();

        double cancellationRate = ((double) cancelled / total) * 100.0;

        return AppointmentStatisticsDTO.builder()
                .totalAppointments(total)
                .completedAppointments(completed)
                .cancelledAppointments(cancelled)
                .cancellationRate(Math.round(cancellationRate * 100.0) / 100.0)
                .build();
    }
}