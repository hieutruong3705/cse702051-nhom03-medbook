package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.dto.slot.EarliestSlotDTO;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;

import jakarta.persistence.LockModeType;

@Repository
public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, Long> {

    /**
     * Đọc một khung giờ và khóa hàng của nó ({@code SELECT … FOR UPDATE}) đến hết giao dịch. Giữ chỗ, đặt lịch và
     * đổi lịch đều đi qua đây trước khi kiểm trạng thái, nên các yêu cầu trên cùng một khung giờ chạy lần lượt.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AppointmentSlot s where s.id = :id")
    Optional<AppointmentSlot> findByIdForUpdate(@Param("id") Long id);

    /**
     * Các khung giờ đặt được sớm nhất cho một dịch vụ dài {@code minutes} phút, bắt đầu từ mốc ({@code fromDate},
     * {@code fromTime}) trở đi và không muộn hơn {@code toDate}; sớm nhất trước, khóa chính làm khóa phụ. Mọi điều kiện nằm
     * trong truy vấn nên số dòng trả về đúng bằng giới hạn của {@code pageable}:
     * còn trống, bác sĩ đang nhận lịch, đủ dài cho dịch vụ (độ dài tính từ giờ kết thúc trừ giờ bắt đầu), không
     * chạm giờ nghỉ của ca, không bị người khác ({@code viewerId} là người đang xem) giữ chỗ còn hạn.
     */
    @Query("""
            select new com.phenikaa.cse702051.medbook.dto.slot.EarliestSlotDTO(
                s.id, d.id, d.fullName, sp.id, sp.name, s.slotDate, s.startTime, s.endTime)
            from AppointmentSlot s
            join s.doctor d
            left join d.specialty sp
            where s.isAvailable = true
              and s.status = 'AVAILABLE'
              and d.isActive = true
              and (s.slotDate > :fromDate or (s.slotDate = :fromDate and s.startTime >= :fromTime))
              and s.slotDate <= :toDate
              and (:doctorId is null or d.id = :doctorId)
              and (:specialtyId is null or sp.id = :specialtyId)
              and (hour(s.endTime) * 60 + minute(s.endTime))
                  - (hour(s.startTime) * 60 + minute(s.startTime)) >= :minutes
              and not exists (
                  select h.id from SlotHold h
                  where h.slotId = s.id and h.expiresAt > :now and h.userId <> :viewerId)
              and not exists (
                  select b.id from ScheduleBreak b
                  join b.doctorSchedule ds
                  where ds.doctor = d and ds.workDate = s.slotDate
                    and s.startTime < b.endTime and s.endTime > b.startTime)
            order by s.slotDate asc, s.startTime asc, s.id asc
            """)
    List<EarliestSlotDTO> findEarliest(
            @Param("minutes") int minutes,
            @Param("fromDate") LocalDate fromDate,
            @Param("fromTime") LocalTime fromTime,
            @Param("toDate") LocalDate toDate,
            @Param("doctorId") Long doctorId,
            @Param("specialtyId") Long specialtyId,
            @Param("viewerId") Long viewerId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    List<AppointmentSlot> findByDoctorIdAndSlotDateAndIsAvailableTrue(Long doctorId, LocalDate slotDate);

    /** Mọi slot của bác sĩ trong một ngày (kể cả đã đặt/đã gỡ), theo giờ bắt đầu. */
    List<AppointmentSlot> findByDoctorIdAndSlotDateOrderByStartTimeAsc(Long doctorId, LocalDate slotDate);

    /** Slot của bác sĩ trong khoảng ngày — dùng để tổng hợp số slot cho cả trang danh sách ca (tránh N+1). */
    List<AppointmentSlot> findByDoctorIdAndSlotDateBetween(Long doctorId, LocalDate from, LocalDate to);

    /** Mọi slot của một bác sĩ (dùng khi xóa hẳn hồ sơ bác sĩ chưa có lịch sử). */
    List<AppointmentSlot> findByDoctorId(Long doctorId);

    /** Slot ở một trạng thái của bác sĩ từ một ngày trở đi (dùng khi bác sĩ ngừng nhận lịch). */
    List<AppointmentSlot> findByDoctorIdAndStatusAndSlotDateGreaterThanEqual(
            Long doctorId, String status, LocalDate from);
}