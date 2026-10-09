package com.phenikaa.cse702051.medbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.Patient;

@Repository
public interface JpaPatientRepository extends JpaRepository<Patient, Long> {
}