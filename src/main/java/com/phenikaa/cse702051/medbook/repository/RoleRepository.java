package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    Optional<Role> findByName(String name);

    List<Role> findByCodeContainingIgnoreCase(String code);

    boolean existsByCode(String code);
}