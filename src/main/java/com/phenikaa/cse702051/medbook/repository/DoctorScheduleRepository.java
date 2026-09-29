package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {

    List<DoctorSchedule> findByDoctorId(Long doctorId);

    List<DoctorSchedule> findByScheduleDate(LocalDate scheduleDate);

    List<DoctorSchedule> findByDoctorIdAndScheduleDate(
            Long doctorId,
            LocalDate scheduleDate
    );

    List<DoctorSchedule> findByDoctorIdAndStatus(
            Long doctorId,
            String status
    );

    List<DoctorSchedule> findByScheduleDateAndStatus(
            LocalDate scheduleDate,
            String status
    );

    boolean existsByDoctorIdAndScheduleDateAndStartTime(
            Long doctorId,
            LocalDate scheduleDate,
            java.time.LocalTime startTime
    );
}