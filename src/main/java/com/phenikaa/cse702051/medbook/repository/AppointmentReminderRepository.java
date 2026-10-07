package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;

/**
 * Truy vấn riêng cho việc nhắc lịch khám. Khoảng thời gian là nửa mở {@code (from, to]} trên cặp (ngày khám, giờ
 * bắt đầu) của slot, nên mỗi lịch trả về đều thật sự đến hạn nhắc và một lô không bị các lịch chưa tới hạn chiếm chỗ.
 */
public interface AppointmentReminderRepository extends Repository<Appointment, Long> {

    /** Lịch còn BOOKED, chưa nhắc mốc 24 giờ, có giờ khám trong {@code (from, to]}; sớm nhất trước. */
    @Query("""
            select a from Appointment a join fetch a.slot s join fetch a.doctor d
            where a.status = :booked and a.reminder24hSentAt is null
              and (s.slotDate > :fromDate or (s.slotDate = :fromDate and s.startTime > :fromTime))
              and (s.slotDate < :toDate or (s.slotDate = :toDate and s.startTime <= :toTime))
            order by s.slotDate asc, s.startTime asc, a.id asc
            """)
    List<Appointment> findDueFor24h(
            @Param("booked") AppointmentStatus booked,
            @Param("fromDate") LocalDate fromDate, @Param("fromTime") LocalTime fromTime,
            @Param("toDate") LocalDate toDate, @Param("toTime") LocalTime toTime,
            Pageable limit);

    /** Lịch còn BOOKED, chưa nhắc mốc 2 giờ, có giờ khám trong {@code (from, to]}; sớm nhất trước. */
    @Query("""
            select a from Appointment a join fetch a.slot s join fetch a.doctor d
            where a.status = :booked and a.reminder2hSentAt is null
              and (s.slotDate > :fromDate or (s.slotDate = :fromDate and s.startTime > :fromTime))
              and (s.slotDate < :toDate or (s.slotDate = :toDate and s.startTime <= :toTime))
            order by s.slotDate asc, s.startTime asc, a.id asc
            """)
    List<Appointment> findDueFor2h(
            @Param("booked") AppointmentStatus booked,
            @Param("fromDate") LocalDate fromDate, @Param("fromTime") LocalTime fromTime,
            @Param("toDate") LocalDate toDate, @Param("toTime") LocalTime toTime,
            Pageable limit);

    /**
     * Giành quyền nhắc mốc 24 giờ một cách nguyên tử: chỉ trả 1 với lịch còn BOOKED và chưa được nhắc, nên hai
     * lần chạy (hoặc hai máy chủ) không bao giờ cùng nhắc một lịch.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Appointment a set a.reminder24hSentAt = :now
            where a.id = :id and a.reminder24hSentAt is null and a.status = :booked
            """)
    int claim24h(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("booked") AppointmentStatus booked);

    /** Giành quyền nhắc mốc 2 giờ; cùng cơ chế với {@link #claim24h}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Appointment a set a.reminder2hSentAt = :now
            where a.id = :id and a.reminder2hSentAt is null and a.status = :booked
            """)
    int claim2h(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("booked") AppointmentStatus booked);

    @Query("select a from Appointment a join fetch a.slot join fetch a.doctor where a.id = :id")
    Optional<Appointment> findWithSlotAndDoctor(@Param("id") Long id);
}
