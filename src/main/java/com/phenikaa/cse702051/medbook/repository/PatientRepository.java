package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByUserId(Long userId);
    Optional<Patient> findByPatientCode(String patientCode);
    boolean existsByPatientCode(String patientCode);
    Optional<Patient> findByUserUsername(String username);

    /** Tìm theo tên hoặc mã bệnh nhân. {@code pattern} đã ở dạng chữ thường kèm % (ví dụ %an%). */
    @Query("""
            select p from Patient p
            where lower(p.fullName) like :pattern or lower(p.patientCode) like :pattern
            """)
    Page<Patient> searchAll(@Param("pattern") String pattern, Pageable pageable);

    /**
     * Bệnh nhân thuộc phạm vi phụ trách của bác sĩ: có lịch hẹn không bị hủy, hoặc có lần khám
     * với bác sĩ đó (khớp quy tắc của {@code DoctorScopeService}).
     */
    @Query("""
            select p from Patient p
            where (lower(p.fullName) like :pattern or lower(p.patientCode) like :pattern)
              and (p.id in (select a.patientId from Appointment a
                            where a.doctor.id = :doctorId and a.status <> :cancelled)
                   or p.id in (select m.patient.id from MedicalRecord m
                               where m.id in (select e.medicalRecordId from Encounter e
                                              where e.doctorId = :doctorId)))
            """)
    Page<Patient> searchForDoctor(
            @Param("doctorId") Long doctorId,
            @Param("pattern") String pattern,
            @Param("cancelled") AppointmentStatus cancelled,
            Pageable pageable);

    /**
     * Tra cứu tại quầy lễ tân: mọi bệnh nhân, theo họ tên, mã bệnh nhân hoặc điện thoại. {@code pattern} lấy từ
     * {@code SearchTerms.toLikePattern} (chữ thường, đã thoát ký tự đại diện bằng {@code !}).
     */
    @Query("""
            select p from Patient p
            where lower(p.fullName) like :pattern escape '!'
               or lower(p.patientCode) like :pattern escape '!'
               or p.phone like :pattern escape '!'
            """)
    Page<Patient> searchForReception(@Param("pattern") String pattern, Pageable pageable);

    /** Đã có hồ sơ trùng họ tên (so chữ thường), ngày sinh và số điện thoại hay chưa. */
    @Query("""
            select count(p) > 0 from Patient p
            where lower(p.fullName) = :fullName and p.dateOfBirth = :dateOfBirth and p.phone = :phone
            """)
    boolean existsByNameBirthAndPhone(
            @Param("fullName") String fullName,
            @Param("dateOfBirth") java.time.LocalDate dateOfBirth,
            @Param("phone") String phone);
}
