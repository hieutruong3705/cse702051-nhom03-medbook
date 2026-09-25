package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.AppointmentSlot;

@Repository
public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, Long> {
    List<AppointmentSlot> findByDoctorIdAndSlotDateAndIsAvailableTrue(Long doctorId, LocalDate slotDate);
}