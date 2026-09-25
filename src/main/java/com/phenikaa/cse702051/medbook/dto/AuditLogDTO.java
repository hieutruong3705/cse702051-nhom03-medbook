package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.AuditLog;

public record AuditLogDTO(
        Long id,
        Long actorUserId,
        String actionCode,
        String entityType,
        Long entityId,
        String ipAddress,
        String userAgent,
        String metadataJson,
        LocalDateTime createdAt
) {
    public static AuditLogDTO from(AuditLog auditLog) {
        Long actorUserId = auditLog.getActorUser() == null ? null : auditLog.getActorUser().getId();
        return new AuditLogDTO(
                auditLog.getId(),
                actorUserId,
                auditLog.getActionCode(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getIpAddress(),
                auditLog.getUserAgent(),
                auditLog.getMetadataJson(),
                auditLog.getCreatedAt());
    }
}
