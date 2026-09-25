package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByUserId(Long userId);

    Optional<Doctor> findByDoctorCode(String doctorCode);

    List<Doctor> findByFullNameContainingIgnoreCase(String fullName);

    List<Doctor> findBySpecialty(String specialty);

    List<Doctor> findBySpecialtyContainingIgnoreCase(String specialty);

    boolean existsByUserId(Long userId);

    boolean existsByDoctorCode(String doctorCode);
}