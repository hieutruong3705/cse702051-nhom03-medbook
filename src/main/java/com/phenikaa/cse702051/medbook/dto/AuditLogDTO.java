package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {

    private Long id;

    private Long actorUserId;

    private String actorUsername;

    private String actorFullName;

    private String actionCode;

    private String entityType;

    private Long entityId;

    private String ipAddress;

    private String userAgent;

    private String metadataJson;

    private LocalDateTime createdAt;
}