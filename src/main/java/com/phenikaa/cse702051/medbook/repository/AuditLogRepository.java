package com.phenikaa.cse702051.medbook.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.model.User;

@Repository
public class AuditLogRepository {

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;

    public AuditLogRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public AuditLog save(AuditLog auditLog) {
        if (auditLog.getCreatedAt() == null) {
            auditLog.setCreatedAt(LocalDateTime.now());
        }

        jdbc().update("""
                INSERT INTO audit_logs (
                    actor_user_id, action_code, entity_type, entity_id,
                    ip_address, user_agent, metadata_json, created_at
                )
                VALUES (
                    :actorUserId, :actionCode, :entityType, :entityId,
                    :ipAddress, :userAgent, :metadataJson, :createdAt
                )
                """, new MapSqlParameterSource()
                .addValue("actorUserId", auditLog.getActorUser() == null ? null : auditLog.getActorUser().getId())
                .addValue("actionCode", auditLog.getActionCode())
                .addValue("entityType", auditLog.getEntityType())
                .addValue("entityId", auditLog.getEntityId())
                .addValue("ipAddress", auditLog.getIpAddress())
                .addValue("userAgent", auditLog.getUserAgent())
                .addValue("metadataJson", auditLog.getMetadataJson())
                .addValue("createdAt", auditLog.getCreatedAt()));
        return auditLog;
    }

    public List<AuditLog> findAll() {
        return jdbc().query("""
                SELECT *
                FROM audit_logs
                ORDER BY created_at DESC
                """, this::mapAuditLog);
    }

    public List<AuditLog> findByActorUserId(Long actorUserId) {
        return jdbc().query("""
                SELECT *
                FROM audit_logs
                WHERE actor_user_id = :actorUserId
                ORDER BY created_at DESC
                """, Map.of("actorUserId", actorUserId), this::mapAuditLog);
    }

    public List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId) {
        return jdbc().query("""
                SELECT *
                FROM audit_logs
                WHERE entity_type = :entityType
                  AND entity_id = :entityId
                ORDER BY created_at DESC
                """, Map.of("entityType", entityType, "entityId", entityId), this::mapAuditLog);
    }

    private AuditLog mapAuditLog(ResultSet resultSet, int rowNumber) throws SQLException {
        User actorUser = null;
        Long actorUserId = nullableLong(resultSet, "actor_user_id");
        if (actorUserId != null) {
            actorUser = new User();
            actorUser.setId(actorUserId);
        }

        return AuditLog.builder()
                .id(resultSet.getLong("id"))
                .actorUser(actorUser)
                .actionCode(resultSet.getString("action_code"))
                .entityType(resultSet.getString("entity_type"))
                .entityId(nullableLong(resultSet, "entity_id"))
                .ipAddress(resultSet.getString("ip_address"))
                .userAgent(resultSet.getString("user_agent"))
                .metadataJson(resultSet.getString("metadata_json"))
                .createdAt(resultSet.getObject("created_at", LocalDateTime.class))
                .build();
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        Number value = (Number) resultSet.getObject(columnName);
        return value == null ? null : value.longValue();
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Database access is not configured");
        }
        return jdbcTemplate;
    }
}
