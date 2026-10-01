package com.phenikaa.cse702051.medbook.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.MedicalRecord;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    // Dùng @Query tường minh: MedicalRecord có getter phụ getPatientId() nên Spring Data sẽ
    // sinh "m.patientId" (không phải thuộc tính JPA) nếu để suy ra tên phương thức.
    @Query("select m from MedicalRecord m where m.patient.id = :patientId")
    Optional<MedicalRecord> findByPatientId(@Param("patientId") Long patientId);

    @Query("select count(m) > 0 from MedicalRecord m where m.patient.id = :patientId")
    boolean existsByPatientId(@Param("patientId") Long patientId);
}
