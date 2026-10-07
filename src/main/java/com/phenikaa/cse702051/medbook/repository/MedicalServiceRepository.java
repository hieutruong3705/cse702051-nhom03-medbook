package com.phenikaa.cse702051.medbook.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.phenikaa.cse702051.medbook.model.MedicalService;

public interface MedicalServiceRepository
        extends JpaRepository<MedicalService, Long>, JpaSpecificationExecutor<MedicalService> {

    Optional<MedicalService> findByCode(String code);

    /** Mã duy nhất không phân biệt hoa thường (mã luôn được lưu ở dạng viết hoa). */
    boolean existsByCodeIgnoreCase(String code);
}
