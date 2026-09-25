package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {

    List<PrescriptionItem> findByPrescriptionId(Long prescriptionId);

    List<PrescriptionItem> findByMedicineNameContainingIgnoreCase(
            String medicineName
    );

    boolean existsByPrescriptionId(Long prescriptionId);
}