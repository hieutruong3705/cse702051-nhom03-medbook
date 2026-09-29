package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByUserId(Long userId);

    Optional<Patient> findByPatientCode(String patientCode);

    List<Patient> findByFullNameContainingIgnoreCase(String fullName);

    List<Patient> findByPhone(String phone);

    boolean existsByUserId(Long userId);

    boolean existsByPatientCode(String patientCode);
}