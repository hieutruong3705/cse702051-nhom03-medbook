package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.audit.AuditLogDTO;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.repository.AuditLogQueryRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.PageRequests;

import jakarta.persistence.criteria.Predicate;

/**
 * Tra cứu nhật ký audit cho Admin (YCCN-23): lọc theo người thực hiện, mã hành động, đối tượng và khoảng ngày;
 * luôn phân trang, mới nhất trước. Việc ghi audit nằm ở {@link AuditLogService}.
 */
@Service
public class AuditLogQueryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final AuditLogQueryRepository repository;
    private final CurrentUserService currentUserService;

    public AuditLogQueryService(AuditLogQueryRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    /**
     * @param from ngày đầu tiên được tính (từ 00:00), có thể bỏ trống
     * @param to   ngày cuối cùng được tính (hết ngày), có thể bỏ trống
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogDTO> search(Long actorUserId, String actionCode, String entityType, Long entityId,
            LocalDate from, LocalDate to, int page, int size) {
        currentUserService.requireRole("ADMIN");
        if (from != null && to != null && from.isAfter(to)) {
            throw new FieldValidationException("to", "Ngày kết thúc không được trước ngày bắt đầu");
        }
        Specification<AuditLog> filter = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (actorUserId != null) {
                all.add(cb.equal(root.get("actorUser").get("id"), actorUserId));
            }
            if (actionCode != null && !actionCode.isBlank()) {
                all.add(cb.equal(root.get("actionCode"), actionCode.trim().toUpperCase(Locale.ROOT)));
            }
            if (entityType != null && !entityType.isBlank()) {
                all.add(cb.equal(root.get("entityType"), entityType.trim().toLowerCase(Locale.ROOT)));
            }
            if (entityId != null) {
                all.add(cb.equal(root.get("entityId"), entityId));
            }
            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                all.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
        Page<AuditLog> result = repository.findAll(filter, PageRequests.of(page, size, NEWEST_FIRST));
        return PageResponse.from(result, AuditLogDTO::from);
    }

    /** Các mã hành động đã xuất hiện trong nhật ký, theo thứ tự chữ cái. */
    @Transactional(readOnly = true)
    public List<String> actionCodes() {
        currentUserService.requireRole("ADMIN");
        return repository.findDistinctActionCodes();
    }
}
