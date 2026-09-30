package com.phenikaa.cse702051.medbook.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.AuditLogDTO;
import com.phenikaa.cse702051.medbook.service.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AuditLogDTO> listAuditLogs(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            HttpServletRequest request) {
        return auditLogService.listAuditLogs(actorUserId, entityType, entityId, request);
    }
}
