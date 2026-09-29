package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.MedicalService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicalServiceRepository extends JpaRepository<MedicalService, Long> {

    Optional<MedicalService> findByCode(String code);

    List<MedicalService> findByNameContainingIgnoreCase(String name);

    List<MedicalService> findByStatus(String status);

    List<MedicalService> findByStatusAndNameContainingIgnoreCase(
            String status,
            String name
    );

    boolean existsByCode(String code);
}