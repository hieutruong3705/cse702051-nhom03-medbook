package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByActionCodeIgnoreCase(String actionCode, Pageable pageable);

    Page<AuditLog> findByEntityTypeIgnoreCase(String entityType, Pageable pageable);

    Page<AuditLog> findByActorUserId(Long actorUserId, Pageable pageable);
}