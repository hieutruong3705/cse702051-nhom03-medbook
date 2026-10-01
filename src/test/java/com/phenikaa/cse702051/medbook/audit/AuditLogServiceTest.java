package com.phenikaa.cse702051.medbook.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.repository.AuditLogRepository;
import com.phenikaa.cse702051.medbook.security.AuthenticatedUser;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.service.AuditEvent;
import com.phenikaa.cse702051.medbook.service.AuditLogService;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/** BE-02: AuditLogService — đủ trường, độc lập giao dịch chính, không tin header giả IP. */
class AuditLogServiceTest extends AbstractApiTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void loginAs(long userId, String username, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, username, Set.of(role));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("record() lấy người thực hiện từ JWT, IP/UA từ request và lưu metadata JSON")
    void recordCapturesActorRequestAndMetadata() throws Exception {
        loginAs(DOCTOR1_USER_ID, "doctor1", "DOCTOR");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");
        request.addHeader("User-Agent", "AuditTest/1.0");
        bindRequest(request);

        AuditLog saved = auditLogService.record(
                AuditEvent.of(AuditActions.ENCOUNTER_VIEW, AuditActions.ENTITY_ENCOUNTERS, 77L)
                        .with("appointmentId", 5)
                        .with("note", "xem chi tiết"));

        AuditLog loaded = auditLogRepository.findById(saved.getId()).orElseThrow();
        assertEquals(AuditActions.ENCOUNTER_VIEW, loaded.getActionCode());
        assertEquals("encounters", loaded.getEntityType());
        assertEquals(77L, loaded.getEntityId());
        assertEquals(DOCTOR1_USER_ID, loaded.getActorUser().getId());
        assertEquals("192.168.1.50", loaded.getIpAddress());
        assertEquals("AuditTest/1.0", loaded.getUserAgent());
        assertNotNull(loaded.getCreatedAt());
        JsonNode metadata = new ObjectMapper().readTree(loaded.getMetadataJson());
        assertEquals(5, metadata.get("appointmentId").asInt());
        assertEquals("xem chi tiết", metadata.get("note").asText());
    }

    @Test
    @DisplayName("Không tin X-Forwarded-For khi chưa bật medbook.trust-proxy (chống giả IP)")
    void forwardedForHeaderIsIgnoredByDefault() {
        loginAs(DOCTOR1_USER_ID, "doctor1", "DOCTOR");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 7.7.7.7");
        bindRequest(request);

        AuditLog saved = auditLogService.record(
                AuditEvent.of(AuditActions.PATIENT_VIEW, AuditActions.ENTITY_PATIENTS, 1L));

        assertEquals("10.0.0.9", auditLogRepository.findById(saved.getId()).orElseThrow().getIpAddress());
    }

    @Test
    @DisplayName("Sự kiện của khách (không đăng nhập) được ghi với actor rỗng; actor chỉ định tường minh được dùng")
    void anonymousAndExplicitActor() {
        AuditLog anonymous = auditLogService.record(
                AuditEvent.of(AuditActions.LOGIN_FAILED, AuditActions.ENTITY_USERS, null).with("username", "nobody"));
        assertNull(auditLogRepository.findById(anonymous.getId()).orElseThrow().getActorUser());

        AuditLog explicit = auditLogService.record(
                AuditEvent.of(AuditActions.LOGIN_SUCCESS, AuditActions.ENTITY_USERS, PATIENT1_USER_ID)
                        .byActor(PATIENT1_USER_ID));
        assertEquals(PATIENT1_USER_ID,
                auditLogRepository.findById(explicit.getId()).orElseThrow().getActorUser().getId());
    }

    @Test
    @DisplayName("Khóa nhạy cảm trong metadata (password, token, hash...) bị che, không bao giờ lưu nguyên văn")
    void sensitiveMetadataKeysAreMasked() throws Exception {
        AuditLog saved = auditLogService.record(
                AuditEvent.of(AuditActions.PASSWORD_CHANGED, AuditActions.ENTITY_USERS, 1L)
                        .with("password", "Hunter2!")
                        .with("newPasswordHash", "$2a$12$abc")
                        .with("resetToken", "abcdef")
                        .with("username", "admin1"));

        String json = auditLogRepository.findById(saved.getId()).orElseThrow().getMetadataJson();
        JsonNode metadata = new ObjectMapper().readTree(json);
        assertEquals("***", metadata.get("password").asText());
        assertEquals("***", metadata.get("newPasswordHash").asText());
        assertEquals("***", metadata.get("resetToken").asText());
        assertEquals("admin1", metadata.get("username").asText());
        assertTrue(!json.contains("Hunter2!") && !json.contains("$2a$12$abc") && !json.contains("abcdef"));
    }

    @Test
    @DisplayName("Sự kiện thiếu actionCode/entityType bị từ chối")
    void invalidEventsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> auditLogService.record(
                AuditEvent.of(null, AuditActions.ENTITY_USERS, 1L)));
        assertThrows(IllegalArgumentException.class, () -> auditLogService.record(
                AuditEvent.of(AuditActions.LOGIN_SUCCESS, " ", 1L)));
    }

    @Test
    @DisplayName("Log vẫn được lưu khi giao dịch gọi nó bị rollback (REQUIRES_NEW)")
    void auditSurvivesOuterRollback() {
        loginAs(PATIENT2_USER_ID, "patient2", "PATIENT");
        long before = countAudits(AuditActions.ENTITY_MEDICAL_RECORDS, 424242L, AuditActions.ACCESS_DENIED,
                PATIENT2_USER_ID);

        transactionTemplate.executeWithoutResult(status -> {
            auditLogService.recordAccessDenied(AuditActions.ENTITY_MEDICAL_RECORDS, 424242L, "thử rollback");
            status.setRollbackOnly();
        });

        assertEquals(before + 1, countAudits(AuditActions.ENTITY_MEDICAL_RECORDS, 424242L,
                AuditActions.ACCESS_DENIED, PATIENT2_USER_ID));
    }

    @Test
    @DisplayName("Actor không tồn tại → ghi log thất bại (fail-closed), không âm thầm bỏ qua")
    void unknownActorFailsClosed() {
        assertThrows(RuntimeException.class, () -> auditLogService.record(
                AuditEvent.of(AuditActions.MEDICAL_RECORD_VIEW, AuditActions.ENTITY_MEDICAL_RECORDS, 1L)
                        .byActor(987654321L)));
    }
}
