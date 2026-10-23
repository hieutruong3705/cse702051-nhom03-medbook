package com.phenikaa.cse702051.medbook.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;

@Repository
public interface AppointmentRepository
        extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    List<Appointment> findByPatientId(Long patientId);

    // @Query tường minh: Appointment có getter phụ getDoctorId() nên Spring Data sẽ sinh
    // "a.doctorId" (không phải thuộc tính JPA) nếu để suy ra tên phương thức.
    @Query("select a from Appointment a where a.doctor.id = :doctorId")
    List<Appointment> findByDoctorId(@Param("doctorId") Long doctorId);

    /** Dùng cho quy tắc "bác sĩ phụ trách bệnh nhân" (DoctorScopeService). */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.doctor.id = :doctorId and a.patientId = :patientId and a.status <> :status
            """)
    boolean existsByDoctorIdAndPatientIdAndStatusNot(
            @Param("doctorId") Long doctorId,
            @Param("patientId") Long patientId,
            @Param("status") AppointmentStatus status);

    /** Tải kèm slot/bác sĩ/chuyên khoa để dựng DTO danh sách mà không phát sinh N+1. */
    @Override
    @EntityGraph(attributePaths = { "slot", "doctor", "doctor.specialty" })
    Page<Appointment> findAll(Specification<Appointment> spec, Pageable pageable);

    long countBySlotIdAndStatusNot(Long slotId, AppointmentStatus status);

    /** Slot đã từng gắn với lịch hẹn nào (kể cả đã hủy) — loại slot này không được xóa cứng (khóa ngoại). */
    @Query("select count(a) > 0 from Appointment a where a.slot.id = :slotId")
    boolean existsAnyBySlotId(@Param("slotId") Long slotId);

    /** Số lịch còn giữ chỗ trên một slot — bất biến của hệ thống: luôn ≤ 1. */
    default long countActiveBySlotId(Long slotId) {
        return countBySlotIdAndStatusNot(slotId, AppointmentStatus.CANCELLED);
    }

    // ---- Tra theo lô và kiểm tra tồn tại cho module khác (danh mục, bác sĩ, lịch làm việc) ----

    /** Bác sĩ đã từng có lịch hẹn (kể cả đã hủy) hay chưa — quyết định xóa hẳn hay chỉ ngừng hoạt động. */
    @Query("select count(a) > 0 from Appointment a where a.doctor.id = :doctorId")
    boolean existsForDoctor(@Param("doctorId") Long doctorId);

    /** Dịch vụ khám đã được chọn trong lịch hẹn nào hay chưa — quyết định xóa hẳn hay chỉ ngừng sử dụng. */
    @Query("select count(a) > 0 from Appointment a where a.serviceId = :serviceId")
    boolean existsForService(@Param("serviceId") Long serviceId);

    /** Trong các slot cho trước, những slot đã từng gắn với lịch hẹn (không xóa cứng được vì khóa ngoại). */
    @Query("select distinct a.slot.id from Appointment a where a.slot.id in :slotIds")
    List<Long> findSlotIdsWithAnyAppointment(@Param("slotIds") Collection<Long> slotIds);

    /** Cặp (slotId, appointmentId) của các lịch đang giữ chỗ trên những slot cho trước. */
    @Query("""
            select a.slot.id, a.id from Appointment a
            where a.slot.id in :slotIds and a.status <> :cancelled
            """)
    List<Object[]> findActiveBySlotIds(
            @Param("slotIds") Collection<Long> slotIds,
            @Param("cancelled") AppointmentStatus cancelled);
}
