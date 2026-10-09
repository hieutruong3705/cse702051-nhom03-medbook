package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.phenikaa.cse702051.medbook.dto.reception.ReceptionAppointmentDTO;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;

/**
 * Truy vấn chỉ đọc cho quầy lễ tân. Trả thẳng DTO hành chính nên ghi chú và lý do hủy không bao giờ được tải lên bộ
 * nhớ; một trang danh sách là hai truy vấn (dữ liệu và đếm).
 *
 * <p>Mốc ngày là ngày khám (ngày của slot). {@code doctorId}, {@code status}, {@code pattern} để {@code null} nghĩa
 * là không lọc. {@code pattern} lấy từ {@code SearchTerms.toLikePattern} và khớp họ tên, mã hoặc điện thoại của
 * bệnh nhân.
 */
public interface ReceptionAppointmentRepository extends Repository<Appointment, Long> {

    String SELECT = """
            select new com.phenikaa.cse702051.medbook.dto.reception.ReceptionAppointmentDTO(
                a.id, a.status, p.id, p.fullName, p.patientCode, p.phone,
                d.id, d.fullName, sp.id, sp.name, ms.id, ms.name,
                s.id, s.slotDate, s.startTime, s.endTime, a.createdAt, a.cancelledAt)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join d.specialty sp
            left join Patient p on p.id = a.patientId
            left join MedicalService ms on ms.id = a.serviceId
            """;

    String FILTER = """
            where s.slotDate between :from and :to
              and (:doctorId is null or d.id = :doctorId)
              and (:status is null or a.status = :status)
              and (:pattern is null
                   or lower(p.fullName) like :pattern escape '!'
                   or lower(p.patientCode) like :pattern escape '!'
                   or p.phone like :pattern escape '!')
            """;

    @Query(value = SELECT + FILTER + "order by s.slotDate asc, s.startTime asc, a.id asc",
            countQuery = """
            select count(a)
            from Appointment a
            join a.slot s
            join a.doctor d
            left join Patient p on p.id = a.patientId
            """ + FILTER)
    Page<ReceptionAppointmentDTO> search(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("doctorId") Long doctorId,
            @Param("status") AppointmentStatus status,
            @Param("pattern") String pattern,
            Pageable pageable);

    @Query(SELECT + "where a.id = :id")
    Optional<ReceptionAppointmentDTO> findDtoById(@Param("id") Long id);
}
