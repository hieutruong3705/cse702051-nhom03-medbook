package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

    Optional<Specialty> findByCode(String code);

    Optional<Specialty> findByName(String name);

    List<Specialty> findByNameContainingIgnoreCase(String name);

    List<Specialty> findByStatus(String status);

    boolean existsByCode(String code);
}