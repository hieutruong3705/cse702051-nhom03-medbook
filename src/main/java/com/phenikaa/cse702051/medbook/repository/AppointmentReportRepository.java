package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.phenikaa.cse702051.medbook.dto.report.AdminAppointmentDTO;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;

/**
 * Truy vấn chỉ đọc cho trang quản trị lịch hẹn và báo cáo lịch khám. Mọi tổng hợp chạy ở CSDL ({@code group by},
 * {@code count}); danh sách trả thẳng DTO hành chính nên không bao giờ tải ghi chú hay lý do hủy lên bộ nhớ.
 *
 * <p>Mốc ngày là ngày khám (ngày của slot). Tham số {@code doctorId}, {@code specialtyId}, {@code status} để
 * {@code null} nghĩa là không lọc. Bác sĩ được tham chiếu qua {@code a.doctor.id} (không dùng {@code doctorId}
 * vì đó chỉ là getter phụ của entity).
 */
public interface AppointmentReportRepository extends Repository<Appointment, Long> {

    @Query(value = """
            select new com.phenikaa.cse702051.medbook.dto.report.AdminAppointmentDTO(
                a.id, p.fullName, p.patientCode, d.fullName, sp.name, ms.name,
                s.slotDate, s.startTime, s.endTime, a.status, a.createdAt)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join d.specialty sp
            left join Patient p on p.id = a.patientId
            left join MedicalService ms on ms.id = a.serviceId
            where s.slotDate between :from and :to
              and (:doctorId is null or d.id = :doctorId)
              and (:specialtyId is null or sp.id = :specialtyId)
              and (:status is null or a.status = :status)
            order by s.slotDate desc, s.startTime desc, a.id desc
            """,
            countQuery = """
            select count(a)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join d.specialty sp
            where s.slotDate between :from and :to
              and (:doctorId is null or d.id = :doctorId)
              and (:specialtyId is null or sp.id = :specialtyId)
              and (:status is null or a.status = :status)
            """)
    Page<AdminAppointmentDTO> search(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId,
            @Param("status") AppointmentStatus status,
            Pageable pageable);

    /** Toàn bộ lịch hẹn khớp bộ lọc theo thứ tự thời gian tăng dần, để xuất tệp (người gọi đã chặn số dòng). */
    @Query("""
            select new com.phenikaa.cse702051.medbook.dto.report.AdminAppointmentDTO(
                a.id, p.fullName, p.patientCode, d.fullName, sp.name, ms.name,
                s.slotDate, s.startTime, s.endTime, a.status, a.createdAt)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join d.specialty sp
            left join Patient p on p.id = a.patientId
            left join MedicalService ms on ms.id = a.serviceId
            where s.slotDate between :from and :to
              and (:doctorId is null or d.id = :doctorId)
              and (:specialtyId is null or sp.id = :specialtyId)
            order by s.slotDate asc, s.startTime asc, a.id asc
            """)
    List<AdminAppointmentDTO> findForExport(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId);

    @Query("""
            select count(a)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join d.specialty sp
            where s.slotDate between :from and :to
              and (:doctorId is null or d.id = :doctorId)
              and (:specialtyId is null or sp.id = :specialtyId)
            """)
    long countMatching(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId);

    /** Mỗi dòng: {@code [AppointmentStatus, Long số lịch]}. */
    @Query("""
            select a.status, count(a)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join d.specialty sp
            where s.slotDate between :from and :to
              and (:doctorId is null or d.id = :doctorId)
              and (:specialtyId is null or sp.id = :specialtyId)
            group by a.status
            """)
    List<Object[]> countByStatus(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId);

    /** Mỗi dòng: {@code [LocalDate ngày khám, AppointmentStatus, Long số lịch]}, ngày tăng dần. */
    @Query("""
            select s.slotDate, a.status, count(a)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join d.specialty sp
            where s.slotDate between :from and :to
              and (:doctorId is null or d.id = :doctorId)
              and (:specialtyId is null or sp.id = :specialtyId)
            group by s.slotDate, a.status
            order by s.slotDate asc
            """)
    List<Object[]> countByDayAndStatus(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId);

    /** Mỗi dòng: {@code [Integer năm, Integer tháng, AppointmentStatus, Long số lịch]}, tháng tăng dần. */
    @Query("""
            select year(s.slotDate), month(s.slotDate), a.status, count(a)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join d.specialty sp
            where s.slotDate between :from and :to
              and (:doctorId is null or d.id = :doctorId)
              and (:specialtyId is null or sp.id = :specialtyId)
            group by year(s.slotDate), month(s.slotDate), a.status
            order by year(s.slotDate), month(s.slotDate)
            """)
    List<Object[]> countByMonthAndStatus(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId);

    /** Mỗi dòng: {@code [Integer năm, AppointmentStatus, Long số lịch]}, năm tăng dần. */
    @Query("""
            select year(s.slotDate), a.status, count(a)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join d.specialty sp
            where s.slotDate between :from and :to
              and (:doctorId is null or d.id = :doctorId)
              and (:specialtyId is null or sp.id = :specialtyId)
            group by year(s.slotDate), a.status
            order by year(s.slotDate)
            """)
    List<Object[]> countByYearAndStatus(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId);

    /** Mỗi dòng: {@code [Long id bác sĩ, String tên bác sĩ, AppointmentStatus, Long số lịch]}, theo tên. */
    @Query("""
            select d.id, d.fullName, a.status, count(a)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join d.specialty sp
            where s.slotDate between :from and :to
              and (:doctorId is null or d.id = :doctorId)
              and (:specialtyId is null or sp.id = :specialtyId)
            group by d.id, d.fullName, a.status
            order by d.fullName asc, d.id asc
            """)
    List<Object[]> countByDoctorAndStatus(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId);

    /**
     * Mỗi dòng: {@code [Long id chuyên khoa, String tên chuyên khoa, AppointmentStatus, Long số lịch]}; bác sĩ chưa
     * gán chuyên khoa cho id và tên {@code null}.
     */
    @Query("""
            select sp.id, sp.name, a.status, count(a)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join d.specialty sp
            where s.slotDate between :from and :to
              and (:doctorId is null or d.id = :doctorId)
              and (:specialtyId is null or sp.id = :specialtyId)
            group by sp.id, sp.name, a.status
            order by sp.name asc, sp.id asc
            """)
    List<Object[]> countBySpecialtyAndStatus(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId);
}
