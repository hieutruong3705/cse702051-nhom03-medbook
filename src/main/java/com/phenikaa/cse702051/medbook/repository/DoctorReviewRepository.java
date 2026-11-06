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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.phenikaa.cse702051.medbook.model.DoctorReview;

/**
 * Đánh giá lần khám. Danh sách luôn nạp kèm bệnh nhân, bác sĩ và chuyên khoa trong cùng truy vấn
 * ({@code @EntityGraph}) để tránh truy vấn lặp khi dựng DTO; điểm trung bình được gộp bằng GROUP BY.
 */
public interface DoctorReviewRepository
        extends JpaRepository<DoctorReview, Long>, JpaSpecificationExecutor<DoctorReview> {

    @Override
    @EntityGraph(attributePaths = { "patient", "doctor", "doctor.specialty" })
    Page<DoctorReview> findAll(Specification<DoctorReview> spec, Pageable pageable);

    @EntityGraph(attributePaths = { "patient", "doctor", "doctor.specialty" })
    Optional<DoctorReview> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = { "patient", "doctor", "doctor.specialty" })
    Optional<DoctorReview> findByAppointmentId(Long appointmentId);

    boolean existsByAppointmentId(Long appointmentId);

    /** [doctorId, điểm trung bình, số đánh giá] của các bác sĩ trong {@code doctorIds}, chỉ tính đánh giá đang hiện. */
    @Query("""
            SELECT r.doctor.id, AVG(r.rating), COUNT(r) FROM DoctorReview r
            WHERE r.doctor.id IN :doctorIds AND r.status = 'VISIBLE'
            GROUP BY r.doctor.id""")
    List<Object[]> summarizeVisibleByDoctorIds(@Param("doctorIds") Collection<Long> doctorIds);

    /** [số sao, số đánh giá] của một bác sĩ, chỉ tính đánh giá đang hiện. */
    @Query("""
            SELECT r.rating, COUNT(r) FROM DoctorReview r
            WHERE r.doctor.id = :doctorId AND r.status = 'VISIBLE'
            GROUP BY r.rating""")
    List<Object[]> countVisibleByRating(@Param("doctorId") Long doctorId);

    /** [điểm trung bình, số đánh giá] toàn phòng khám, chỉ tính đánh giá đang hiện của bác sĩ đang hoạt động. */
    @Query("""
            SELECT AVG(r.rating), COUNT(r) FROM DoctorReview r
            WHERE r.status = 'VISIBLE' AND r.doctor.isActive = true""")
    List<Object[]> summarizeVisible();
}
