package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.DoctorSchedule;

@Repository
public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    List<DoctorSchedule> findByDoctorId(Long doctorId);

    /** Các ca của bác sĩ trong một ngày — dùng để phát hiện ca chồng nhau. */
    List<DoctorSchedule> findByDoctorIdAndWorkDate(Long doctorId, LocalDate workDate);

    Page<DoctorSchedule> findByDoctorIdAndWorkDateBetween(
            Long doctorId, LocalDate from, LocalDate to, Pageable pageable);
}