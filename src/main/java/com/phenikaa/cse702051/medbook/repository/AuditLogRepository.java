package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByActorUserId(Long actorUserId);

    List<AuditLog> findByActionCode(String actionCode);

    List<AuditLog> findByEntityTypeAndEntityId(
            String entityType,
            Long entityId
    );

    List<AuditLog> findByActorUserIdAndActionCode(
            Long actorUserId,
            String actionCode
    );
}