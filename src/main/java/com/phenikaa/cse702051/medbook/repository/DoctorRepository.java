package com.phenikaa.cse702051.medbook.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.phenikaa.cse702051.medbook.model.Doctor;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findBySpecialtyIdAndIsActiveTrue(Long specialtyId);
}