package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.UserRole;
import com.phenikaa.cse702051.medbook.model.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role WHERE ur.user.id = :userId")
    List<UserRole> findByUserIdWithRole(@Param("userId") Long userId);

    @Query("SELECT r.code FROM UserRole ur JOIN ur.role r WHERE ur.user.id = :userId")
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
