package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    @Query(
        value = """
                SELECT r.code
                FROM roles r
                INNER JOIN user_roles ur
                    ON ur.role_id = r.id
                WHERE ur.user_id = :userId
                """,
        nativeQuery = true
    )
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    List<UserRole> findByUserId(Long userId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);
}