package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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