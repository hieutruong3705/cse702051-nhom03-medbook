package com.phenikaa.cse702051.medbook.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Một sự kiện cần ghi audit. Bất biến: {@link #with} và {@link #byActor} trả bản sao mới.
 * {@code actorUserId} để trống thì {@link AuditLogService} lấy từ người dùng hiện tại
 * (có thể rỗng với sự kiện của khách, ví dụ đăng nhập thất bại).
 */
public record AuditEvent(
        String actionCode,
        String entityType,
        Long entityId,
        Map<String, Object> metadata,
        Long actorUserId
) {

    public AuditEvent {
        // LinkedHashMap cho phép giá trị null và giữ thứ tự khóa (Map.copyOf thì không).
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public static AuditEvent of(String actionCode, String entityType, Long entityId) {
        return new AuditEvent(actionCode, entityType, entityId, Map.of(), null);
    }

    public AuditEvent with(String key, Object value) {
        Map<String, Object> next = new LinkedHashMap<>(metadata);
        next.put(key, value);
        return new AuditEvent(actionCode, entityType, entityId, next, actorUserId);
    }

    public AuditEvent byActor(Long userId) {
        return new AuditEvent(actionCode, entityType, entityId, metadata, userId);
    }
}
