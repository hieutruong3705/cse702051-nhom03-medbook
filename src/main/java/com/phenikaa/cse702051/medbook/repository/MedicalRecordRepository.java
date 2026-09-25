package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    Optional<MedicalRecord> findByPatientId(Long patientId);

    Optional<MedicalRecord> findByRecordCode(String recordCode);

    boolean existsByPatientId(Long patientId);

    boolean existsByRecordCode(String recordCode);
}