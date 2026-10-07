package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.DoctorDayOff;

@Repository
public interface DoctorDayOffRepository extends JpaRepository<DoctorDayOff, Long> {

    boolean existsByDoctorIdAndOffDate(Long doctorId, LocalDate offDate);

    List<DoctorDayOff> findByDoctorIdAndOffDateBetweenOrderByOffDateAsc(Long doctorId, LocalDate from, LocalDate to);
}
