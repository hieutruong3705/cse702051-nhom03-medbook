package com.phenikaa.cse702051.medbook.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.phenikaa.cse702051.medbook.model.Prescription;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByEncounterId(Long encounterId);

    /** Các đơn thuốc của một lần khám theo thứ tự kê. */
    List<Prescription> findByEncounterIdOrderByIdAsc(Long encounterId);

    Optional<Prescription> findByPrescriptionCode(String prescriptionCode);

    boolean existsByPrescriptionCode(String prescriptionCode);
}
