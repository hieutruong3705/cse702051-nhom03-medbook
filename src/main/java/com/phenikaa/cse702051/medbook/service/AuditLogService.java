package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.dto.AuditLogDTO;
import com.phenikaa.cse702051.medbook.exception.ApiException;
import com.phenikaa.cse702051.medbook.exception.ErrorCode;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.AuditLogRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuditLogService {

    public static final String ACTION_MEDICAL_RECORD_VIEW = "MEDICAL_RECORD_VIEW";
    public static final String ENTITY_MEDICAL_RECORDS = "medical_records";

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;

    public AuditLogService(AuditLogRepository auditLogRepository, CurrentUserService currentUserService) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
    }

    public void recordMedicalRecordView(CurrentUser currentUser, MedicalRecord medicalRecord, HttpServletRequest request) {
        User actor = new User();
        actor.setId(currentUser.userId());

        AuditLog auditLog = AuditLog.builder()
                .actorUser(actor)
                .actionCode(ACTION_MEDICAL_RECORD_VIEW)
                .entityType(ENTITY_MEDICAL_RECORDS)
                .entityId(medicalRecord.getId())
                .ipAddress(resolveClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .metadataJson("{\"accessType\":\"VIEW_MEDICAL_RECORD\"}")
                .createdAt(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
    }

    public List<AuditLogDTO> listAuditLogs(Long actorUserId, String entityType, Long entityId, HttpServletRequest request) {
        CurrentUser currentUser = currentUserService.requireCurrentUser(request);
        if (!currentUser.hasRole("ADMIN")) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Khong co quyen xem audit log");
        }

        if (entityType != null && entityId != null) {
            return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId)
                    .stream()
                    .map(AuditLogDTO::from)
                    .toList();
        }

        if (actorUserId != null) {
            return auditLogRepository.findByActorUserId(actorUserId)
                    .stream()
                    .map(AuditLogDTO::from)
                    .toList();
        }

        return auditLogRepository.findAll()
                .stream()
                .map(AuditLogDTO::from)
                .toList();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
