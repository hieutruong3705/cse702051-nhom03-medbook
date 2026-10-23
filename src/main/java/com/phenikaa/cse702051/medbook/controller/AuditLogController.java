package com.phenikaa.cse702051.medbook.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.audit.AuditLogDTO;
import com.phenikaa.cse702051.medbook.service.AuditLogQueryService;

/**
 * Tra cứu nhật ký audit cho Admin (YCCN-23) tại {@code /admin/audit-logs}. Đường dẫn cũ {@code /audit-logs}
 * (không phân trang) đã được thay bằng đường dẫn này. Chỉ ADMIN (xem {@code SecurityConfig}: {@code /admin/**}).
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    public PageResponse<AuditLogDTO> search(
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String actionCode,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditLogQueryService.search(actorUserId, actionCode, entityType, entityId, from, to, page, size);
    }

    /** Các mã hành động đã xuất hiện, để giao diện dựng ô chọn bộ lọc. */
    @GetMapping("/action-codes")
    public List<String> actionCodes() {
        return auditLogQueryService.actionCodes();
    }
}
