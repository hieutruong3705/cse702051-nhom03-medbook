package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Role;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.model.UserRole;
import com.phenikaa.cse702051.medbook.model.UserRoleId;
import com.phenikaa.cse702051.medbook.repository.RoleRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.repository.UserRoleRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Các thao tác quản trị tài khoản nhạy cảm về bảo mật (YCCN-05, 06): khóa/mở tài khoản và gán
 * quyền. Controller Admin chỉ gọi hai phương thức này.
 *
 * <p>Mọi thay đổi: chỉ ADMIN; khóa dòng người dùng để tuần tự hóa; tăng {@code token_version}
 * (mọi token đã cấp cho người đó lập tức vô hiệu); ghi audit kèm giá trị trước/sau; và bảo vệ chống
 * tự khóa / tự gỡ quyền / làm mất quản trị viên cuối cùng.
 */
@Service
public class AccountSecurityService {

    private static final String ACTIVE = "ACTIVE";
    private static final String LOCKED = "LOCKED";
    private static final String ADMIN = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public AccountSecurityService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    /**
     * Khóa hoặc mở tài khoản.
     *
     * @param status {@code ACTIVE} hoặc {@code LOCKED} (không phân biệt hoa thường)
     * @param reason lý do (lưu vào audit, có thể rỗng)
     */
    @Transactional
    public User changeStatus(Long userId, String status, String reason) {
        CurrentUser actor = currentUserService.requireRole(ADMIN);
        String target = normalizeStatus(status);

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng ID: " + userId));
        String before = user.getStatus();
        if (before.equalsIgnoreCase(target)) {
            return user; // không có gì thay đổi
        }

        if (LOCKED.equals(target)) {
            if (actor.userId().equals(userId)) {
                throw new ConflictException("Không thể tự khóa tài khoản của chính mình!");
            }
            if (hasAdminRole(userId) && userRoleRepository.countActiveAdmins() <= 1) {
                throw new ConflictException("Không thể khóa quản trị viên đang hoạt động cuối cùng!");
            }
            user.setTokenVersion(user.getTokenVersion() + 1); // vô hiệu hóa mọi phiên hiện có
        } else {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }

        user.setStatus(target);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.ACCOUNT_STATUS_CHANGED, AuditActions.ENTITY_USERS, userId)
                .with("from", before)
                .with("to", target)
                .with("reason", reason));
        return user;
    }

    /**
     * Thay toàn bộ vai trò của người dùng bằng {@code roleCodes}.
     *
     * @throws FieldValidationException danh sách rỗng hoặc có mã vai trò không tồn tại
     * @throws ConflictException        tự gỡ quyền ADMIN của mình, hoặc gỡ ADMIN đang hoạt động cuối cùng
     */
    @Transactional
    public User changeRoles(Long userId, Collection<String> roleCodes) {
        CurrentUser actor = currentUserService.requireRole(ADMIN);

        Set<String> requested = roleCodes == null ? Set.of() : roleCodes.stream()
                .map(CurrentUser::normalizeRole)
                .filter(code -> !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) {
            throw new FieldValidationException("roles", "Phải có ít nhất một vai trò");
        }
        Map<String, Role> roles = new LinkedHashMap<>();
        for (String code : requested) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new FieldValidationException("roles", "Vai trò không tồn tại: " + code));
            roles.put(code, role);
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng ID: " + userId));

        List<UserRole> current = userRoleRepository.findByUserIdWithRole(userId);
        Set<String> before = current.stream().map(ur -> ur.getRole().getCode())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        boolean removingAdmin = before.contains(ADMIN) && !requested.contains(ADMIN);
        if (removingAdmin) {
            if (actor.userId().equals(userId)) {
                throw new ConflictException("Không thể tự gỡ quyền ADMIN của chính mình!");
            }
            if (ACTIVE.equalsIgnoreCase(user.getStatus()) && userRoleRepository.countActiveAdmins() <= 1) {
                throw new ConflictException("Không thể gỡ quyền của quản trị viên đang hoạt động cuối cùng!");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<UserRole> toRemove = current.stream()
                .filter(ur -> !requested.contains(ur.getRole().getCode()))
                .toList();
        userRoleRepository.deleteAll(toRemove);
        for (String code : requested) {
            if (!before.contains(code)) {
                Role role = roles.get(code);
                userRoleRepository.save(UserRole.builder()
                        .id(new UserRoleId(userId, role.getId()))
                        .user(user)
                        .role(role)
                        .createdAt(now)
                        .build());
            }
        }

        user.setTokenVersion(user.getTokenVersion() + 1); // quyền đổi ⇒ token cũ (mang quyền cũ) hết hiệu lực
        user.setUpdatedAt(now);
        userRepository.save(user);

        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.ROLE_CHANGED, AuditActions.ENTITY_USERS, userId)
                .with("before", List.copyOf(before))
                .with("after", List.copyOf(requested)));
        return user;
    }

    private boolean hasAdminRole(Long userId) {
        return userRoleRepository.findRoleCodesByUserId(userId).contains(ADMIN);
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE.equals(normalized) && !LOCKED.equals(normalized)) {
            throw new FieldValidationException("status", "Trạng thái phải là ACTIVE hoặc LOCKED");
        }
        return normalized;
    }
}
