package com.phenikaa.cse702051.medbook.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.dto.AuditLogDTO;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.repository.AuditLogRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AuditLogRepository auditLogRepository;

    public Page<AuditLogDTO> search(
            String actionCode,
            String entityType,
            Long actorUserId,
            int page,
            int size) {

        if (page < 0) {
            page = 0;
        }

        if (size <= 0) {
            size = 10;
        }

        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt"
                ).and(
                        Sort.by(Sort.Direction.DESC, "id")
                )
        );

        Specification<AuditLog> specification = (root, query, cb) -> {
            List<Predicate> predicates = new java.util.ArrayList<>();

            if (actionCode != null && !actionCode.isBlank()) {
                predicates.add(
                        cb.equal(
                                cb.upper(root.get("actionCode")),
                                actionCode.trim().toUpperCase()
                        )
                );
            }

            if (entityType != null && !entityType.isBlank()) {
                predicates.add(
                        cb.equal(
                                cb.upper(root.get("entityType")),
                                entityType.trim().toUpperCase()
                        )
                );
            }

            if (actorUserId != null) {
                predicates.add(
                        cb.equal(
                                root.get("actorUser").get("id"),
                                actorUserId
                        )
                );
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return auditLogRepository
                .findAll(specification, pageable)
                .map(this::toDTO);
    }

    private AuditLogDTO toDTO(AuditLog log) {
        Long actorUserId = null;
        String actorUsername = null;
        String actorFullName = null;

        if (log.getActorUser() != null) {
            actorUserId = log.getActorUser().getId();
            actorUsername = log.getActorUser().getUsername();
            actorFullName = log.getActorUser().getFullName();
        }

        return AuditLogDTO.builder()
                .id(log.getId())
                .actorUserId(actorUserId)
                .actorUsername(actorUsername)
                .actorFullName(actorFullName)
                .actionCode(log.getActionCode())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .metadataJson(log.getMetadataJson())
                .createdAt(log.getCreatedAt())
                .build();
    }
        
    public AuditLog record(
            String actionCode,
            String entityType,
            Long entityId,
            String ipAddress,
            String userAgent,
            String metadataJson) {

        AuditLog log = AuditLog.builder()
                .actorUser(null)
                .actionCode(actionCode)
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .metadataJson(metadataJson)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        return auditLogRepository.save(log);
    }

}
