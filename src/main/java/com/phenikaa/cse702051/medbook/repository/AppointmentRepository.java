package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByAppointmentCode(String appointmentCode);

    List<Appointment> findByPatientId(Long patientId);

    List<Appointment> findByDoctorId(Long doctorId);

    List<Appointment> findByAppointmentDate(LocalDate appointmentDate);

    List<Appointment> findByPatientIdAndAppointmentDate(
            Long patientId,
            LocalDate appointmentDate
    );

    List<Appointment> findByDoctorIdAndAppointmentDate(
            Long doctorId,
            LocalDate appointmentDate
    );

    boolean existsByScheduleId(Long scheduleId);

    /*
     * Parameterized query:
     * Truy vấn lịch hẹn của một bệnh nhân theo ngày.
     */
    @Query("""
            SELECT a
            FROM Appointment a
            WHERE a.patientId = :patientId
              AND a.appointmentDate = :appointmentDate
            ORDER BY a.appointmentDate ASC, a.id ASC
            """)
    List<Appointment> findAppointmentsByPatientAndDate(
            @Param("patientId") Long patientId,
            @Param("appointmentDate") LocalDate appointmentDate
    );

    /*
     * Pagination tại tầng Data Access.
     */
    Page<Appointment> findByPatientId(
            Long patientId,
            Pageable pageable
    );
}