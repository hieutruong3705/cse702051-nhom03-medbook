package com.phenikaa.cse702051.medbook.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.Doctor;

import jakarta.persistence.LockModeType;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long>, JpaSpecificationExecutor<Doctor> {

    Optional<Doctor> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /** Tải kèm chuyên khoa để dựng DTO danh sách mà không phát sinh N+1. */
    @Override
    @EntityGraph(attributePaths = "specialty")
    Page<Doctor> findAll(Specification<Doctor> spec, Pageable pageable);

    /** Chuyên khoa đang được hồ sơ bác sĩ nào dùng hay không (quyết định xóa hẳn hay chỉ ngừng sử dụng). */
    boolean existsBySpecialtyId(Long specialtyId);

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);

    boolean existsByLicenseNumberIgnoreCaseAndIdNot(String licenseNumber, Long id);

    /**
     * Trong các bác sĩ cho trước, những người đã có lịch sử (lịch hẹn, ca làm việc hoặc lần khám) và vì vậy
     * không được xóa hẳn. Một truy vấn cho cả trang danh sách.
     */
    @Query("""
            select d.id from Doctor d
            where d.id in :ids
              and (exists (select 1 from Appointment a where a.doctor.id = d.id)
                   or exists (select 1 from DoctorSchedule s where s.doctor.id = d.id)
                   or exists (select 1 from Encounter e where e.doctorId = d.id))
            """)
    List<Long> findIdsWithHistory(@Param("ids") Collection<Long> ids);

    /**
     * Khóa dòng bác sĩ để tuần tự hóa mọi thay đổi lịch làm việc của bác sĩ đó (tạo, sửa ca, giờ nghỉ, ngày nghỉ):
     * hai yêu cầu song song không thể cùng vượt qua kiểm tra chồng ca. Chỉ dùng cho mục đích này.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);
}
