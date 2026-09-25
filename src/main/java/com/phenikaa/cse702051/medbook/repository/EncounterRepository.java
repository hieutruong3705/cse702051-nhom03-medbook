package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    List<Encounter> findByMedicalRecordId(Long medicalRecordId);

    List<Encounter> findByDoctorId(Long doctorId);

    Optional<Encounter> findByAppointmentId(Long appointmentId);

    List<Encounter> findByMedicalRecordIdAndStatus(
            Long medicalRecordId,
            String status
    );

    List<Encounter> findByDoctorIdAndStatus(
            Long doctorId,
            String status
    );

    boolean existsByAppointmentId(Long appointmentId);
}