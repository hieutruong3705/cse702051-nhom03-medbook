package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.dto.AuditLogDTO;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.repository.AuditLogRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Điểm ghi audit duy nhất của hệ thống (YCCN-23, YCPCN-07): người thực hiện, đối tượng,
 * IP, User-Agent, thời gian, hành động.
 *
 * <p>Mọi phương thức ghi chạy trong giao dịch riêng ({@code REQUIRES_NEW}) nên log vẫn được
 * lưu khi giao dịch chính bị rollback (ví dụ truy cập bị từ chối). Lỗi khi ghi log được ném
 * ra ngoài: với dữ liệu y tế, nếu không ghi được audit thì request đọc cũng thất bại
 * (fail-closed).
 */
@Service
public class AuditLogService {

    /** Giữ tương thích mã cũ; dùng {@link AuditActions} cho mã mới. */
    public static final String ACTION_MEDICAL_RECORD_VIEW = AuditActions.MEDICAL_RECORD_VIEW;
    public static final String ENTITY_MEDICAL_RECORDS = AuditActions.ENTITY_MEDICAL_RECORDS;

    private static final Pattern SENSITIVE_KEY = Pattern.compile("(?i).*(password|secret|token|authorization|hash).*");
    private static final int MAX_USER_AGENT = 500;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final boolean trustProxy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            @Value("${medbook.trust-proxy:false}") boolean trustProxy) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.trustProxy = trustProxy;
    }

    /** Ghi một sự kiện audit tổng quát. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(AuditEvent event) {
        return persist(event);
    }

    /** Ghi việc đọc bệnh án (người đọc lấy từ JWT, IP/UA lấy từ request hiện tại). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog recordMedicalRecordView(MedicalRecord medicalRecord) {
        return persist(AuditEvent.of(AuditActions.MEDICAL_RECORD_VIEW, AuditActions.ENTITY_MEDICAL_RECORDS,
                medicalRecord.getId())
                .with("accessType", "VIEW_MEDICAL_RECORD")
                .with("patientId", medicalRecord.getPatientId()));
    }

    /** Ghi một lần truy cập bị từ chối (vẫn lưu dù giao dịch gọi sau đó rollback). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog recordAccessDenied(String entityType, Long entityId, String reason) {
        return persist(AuditEvent.of(AuditActions.ACCESS_DENIED, entityType, entityId)
                .with("reason", reason));
    }

    /**
     * @deprecated dùng {@link #recordMedicalRecordView(MedicalRecord)}; các tham số còn lại bị bỏ qua.
     */
    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog recordMedicalRecordView(CurrentUser currentUser, MedicalRecord medicalRecord,
            HttpServletRequest request) {
        return persist(AuditEvent.of(AuditActions.MEDICAL_RECORD_VIEW, AuditActions.ENTITY_MEDICAL_RECORDS,
                medicalRecord.getId())
                .with("accessType", "VIEW_MEDICAL_RECORD")
                .byActor(currentUser == null ? null : currentUser.userId()));
    }

    /**
     * Danh sách audit tối giản của bản cũ; sẽ thay bằng {@code AuditLogQueryService}
     * có lọc/phân trang ở {@code GET /admin/audit-logs}.
     */
    @Transactional(readOnly = true)
    public List<AuditLogDTO> listAuditLogs(Long actorUserId, String entityType, Long entityId,
            HttpServletRequest request) {
        currentUserService.requireRole("ADMIN");

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

    private AuditLog persist(AuditEvent event) {
        if (event == null || event.actionCode() == null || event.actionCode().isBlank()
                || event.entityType() == null || event.entityType().isBlank()) {
            throw new IllegalArgumentException("Sự kiện audit phải có actionCode và entityType");
        }

        Long actorId = event.actorUserId() != null
                ? event.actorUserId()
                : currentUserService.findCurrentUser().map(CurrentUser::userId).orElse(null);

        HttpServletRequest request = currentRequest();

        AuditLog auditLog = AuditLog.builder()
                .actorUser(actorId == null ? null : userRepository.getReferenceById(actorId))
                .actionCode(event.actionCode())
                .entityType(event.entityType())
                .entityId(event.entityId())
                .ipAddress(request == null ? null : resolveClientIp(request))
                .userAgent(request == null ? null : truncate(request.getHeader("User-Agent"), MAX_USER_AGENT))
                .metadataJson(toJson(event.metadata()))
                .createdAt(LocalDateTime.now())
                .build();

        return auditLogRepository.save(auditLog);
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    /**
     * Chỉ tin {@code X-Forwarded-For} khi chạy sau reverse proxy đáng tin
     * ({@code medbook.trust-proxy=true}); nếu không, client có thể giả IP.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return truncate(forwardedFor.split(",")[0].trim(), 45);
            }
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private String toJson(Map<String, Object> metadata) {
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> safe.put(key, SENSITIVE_KEY.matcher(key).matches() ? "***" : value));
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không chuyển được metadata audit sang JSON", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
