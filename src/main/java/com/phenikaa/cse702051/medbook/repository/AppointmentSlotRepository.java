package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, Long> {

    List<AppointmentSlot> findByDoctorId(Long doctorId);

    List<AppointmentSlot> findByScheduleId(Long scheduleId);

    List<AppointmentSlot> findBySlotDate(LocalDate slotDate);

    List<AppointmentSlot> findByDoctorIdAndSlotDate(
            Long doctorId,
            LocalDate slotDate
    );

    List<AppointmentSlot> findByDoctorIdAndSlotDateAndStatus(
            Long doctorId,
            LocalDate slotDate,
            String status
    );

    boolean existsByScheduleId(Long scheduleId);
}