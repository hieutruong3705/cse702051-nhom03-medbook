package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByUserId(Long userId);
    Optional<Patient> findByPatientCode(String patientCode);
    boolean existsByPatientCode(String patientCode);
    Optional<Patient> findByUserUsername(String username);
}
