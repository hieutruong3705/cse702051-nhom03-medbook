package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByEncounterId(Long encounterId);

    Optional<Prescription> findByPrescriptionCode(String prescriptionCode);

    List<Prescription> findByEncounterIdAndStatus(
            Long encounterId,
            String status
    );

    boolean existsByPrescriptionCode(String prescriptionCode);
}