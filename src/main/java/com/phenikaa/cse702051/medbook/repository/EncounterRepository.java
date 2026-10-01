package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.Encounter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    List<Encounter> findByMedicalRecordId(Long medicalRecordId);

    List<Encounter> findByMedicalRecordIdIn(
            List<Long> medicalRecordIds
    );

    List<Encounter> findByDoctorId(Long doctorId);

    Optional<Encounter> findByAppointmentId(Long appointmentId);

    List<Encounter> findByMedicalRecordIdAndStatus(
            Long medicalRecordId,
            String status
    );

    List<Encounter> findByDoctorIdAndStatus(
            Long doctorId,
            String status
    );

    boolean existsByAppointmentId(Long appointmentId);

    /** Dùng cho quy tắc "bác sĩ phụ trách bệnh nhân" (DoctorScopeService). */
    boolean existsByDoctorIdAndMedicalRecordId(Long doctorId, Long medicalRecordId);

    /** Lịch sử khám của một bệnh án (mới nhất trước do người gọi truyền Sort). */
    Page<Encounter> findByMedicalRecordId(Long medicalRecordId, Pageable pageable);

    /** Các lần khám do một bác sĩ thực hiện trên một bệnh án. */
    Page<Encounter> findByMedicalRecordIdAndDoctorId(Long medicalRecordId, Long doctorId, Pageable pageable);
}