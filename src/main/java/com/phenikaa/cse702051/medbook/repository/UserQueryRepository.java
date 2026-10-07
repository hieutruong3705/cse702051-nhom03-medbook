package com.phenikaa.cse702051.medbook.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.phenikaa.cse702051.medbook.model.User;

/**
 * Truy vấn cho việc quản trị tài khoản: lọc động bằng {@code Specification}, kiểm tra trùng không phân biệt hoa
 * thường, và tra vai trò cùng hồ sơ liên quan theo lô để một trang danh sách không phát sinh N+1.
 */
public interface UserQueryRepository extends Repository<User, Long>, JpaSpecificationExecutor<User> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Mỗi dòng: {@code [Long userId, String mã vai trò]}. */
    @Query("""
            select ur.user.id, r.code from UserRole ur join ur.role r
            where ur.user.id in :userIds
            order by r.code asc
            """)
    List<Object[]> findRoleCodes(@Param("userIds") Collection<Long> userIds);

    /** Mỗi dòng: {@code [Long userId, Long patientId]}. */
    @Query("select p.user.id, p.id from Patient p where p.user.id in :userIds")
    List<Object[]> findPatientIds(@Param("userIds") Collection<Long> userIds);

    /** Mỗi dòng: {@code [Long userId, Long doctorId]}. */
    @Query("select d.userId, d.id from Doctor d where d.userId in :userIds")
    List<Object[]> findDoctorIds(@Param("userIds") Collection<Long> userIds);
}
