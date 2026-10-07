package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.RegisterRequest;
import com.phenikaa.cse702051.medbook.dto.doctor.DoctorProfileData;
import com.phenikaa.cse702051.medbook.dto.user.AdminCreateUserRequest;
import com.phenikaa.cse702051.medbook.dto.user.AdminCreateUserRequest.DoctorProfileInput;
import com.phenikaa.cse702051.medbook.dto.user.AdminUserDTO;
import com.phenikaa.cse702051.medbook.dto.user.ChangeUserRolesRequest;
import com.phenikaa.cse702051.medbook.dto.user.ChangeUserStatusRequest;
import com.phenikaa.cse702051.medbook.dto.user.UpdateProfileRequest;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Role;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.model.UserRole;
import com.phenikaa.cse702051.medbook.model.UserRoleId;
import com.phenikaa.cse702051.medbook.repository.RoleRepository;
import com.phenikaa.cse702051.medbook.repository.UserQueryRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.repository.UserRoleRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.PageRequests;
import com.phenikaa.cse702051.medbook.util.SearchTerms;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Admin quản lý tài khoản (YCCN-05 và phần tạo tài khoản của YCCN-01).
 *
 * <ul>
 * <li><b>Tạo</b>: tài khoản, vai trò và hồ sơ đi kèm (bệnh nhân kèm bệnh án, hoặc hồ sơ bác sĩ) được tạo trong MỘT
 * giao dịch; lỗi ở bước nào cũng hoàn tác tất cả, không để lại tài khoản mồ côi.</li>
 * <li><b>Khóa/mở và đổi vai trò</b>: chỉ chuyển tiếp cho {@link AccountSecurityService}, nơi đã có các quy tắc
 * bảo mật (vô hiệu token cũ, không tự khóa, không làm mất quản trị viên cuối cùng, ghi audit).</li>
 * <li>Không bao giờ trả băm mật khẩu hay phiên bản token ra ngoài.</li>
 * </ul>
 */
@Service
public class AdminUserService {

    private static final Set<String> ROLES = Set.of("PATIENT", "DOCTOR", "ADMIN");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "LOCKED");
    private static final Set<String> SORTS = Set.of("id", "username", "createdAt");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));
    private static final int MAX_DOCTOR_PHONE = 20;

    private final UserRepository userRepository;
    private final UserQueryRepository userQueryRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountSecurityService accountSecurityService;
    private final UserService userService;
    private final PatientService patientService;
    private final DoctorService doctorService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public AdminUserService(
            UserRepository userRepository,
            UserQueryRepository userQueryRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            AccountSecurityService accountSecurityService,
            UserService userService,
            PatientService patientService,
            DoctorService doctorService,
            CurrentUserService currentUserService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.userQueryRepository = userQueryRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountSecurityService = accountSecurityService;
        this.userService = userService;
        this.patientService = patientService;
        this.doctorService = doctorService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    // ================= Đọc =================

    /**
     * @param keyword khớp tên đăng nhập, email hoặc họ tên
     * @param sort    {@code id}, {@code username} hoặc {@code createdAt}, có thể kèm {@code ,asc|desc}
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserDTO> list(String keyword, String role, String status, int page, int size,
            String sort) {
        currentUserService.requireRole("ADMIN");
        String roleFilter = oneOf("role", role, ROLES, "Vai trò phải là PATIENT, DOCTOR hoặc ADMIN");
        String statusFilter = oneOf("status", status, STATUSES, "Trạng thái phải là ACTIVE hoặc LOCKED");
        Page<User> result = userQueryRepository.findAll(matching(keyword, roleFilter, statusFilter),
                PageRequests.of(page, size, PageRequests.parseSort(sort, SORTS, NEWEST_FIRST)));
        List<AdminUserDTO> content = toDTOs(result.getContent());
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminUserDTO get(Long id) {
        currentUserService.requireRole("ADMIN");
        return toDTO(find(id));
    }

    // ================= Tạo và sửa =================

    @Transactional
    public AdminUserDTO create(AdminCreateUserRequest request) {
        currentUserService.requireRole("ADMIN");
        String roleCode = request.role().trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(roleCode)) {
            throw new FieldValidationException("role", "Vai trò phải là PATIENT, DOCTOR hoặc ADMIN");
        }
        boolean doctor = "DOCTOR".equals(roleCode);
        if (doctor && request.doctorProfile() == null) {
            throw new FieldValidationException("doctorProfile", "Tài khoản bác sĩ phải kèm thông tin hồ sơ bác sĩ");
        }
        if (!doctor && request.doctorProfile() != null) {
            throw new FieldValidationException("doctorProfile", "Chỉ tài khoản bác sĩ mới có hồ sơ bác sĩ");
        }

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userQueryRepository.existsByUsernameIgnoreCase(username)) {
            throw new FieldConflictException("username", "Tên đăng nhập đã được sử dụng");
        }
        if (userQueryRepository.existsByEmailIgnoreCase(email)) {
            throw new FieldConflictException("email", "Email đã được tài khoản khác sử dụng");
        }
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new FieldValidationException("role", "Vai trò không tồn tại: " + roleCode));

        LocalDateTime now = LocalDateTime.now();
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        User user;
        try {
            user = userRepository.saveAndFlush(User.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(request.password()))
                    .fullName(request.fullName().trim())
                    .email(email)
                    .phone(phone)
                    .status("ACTIVE")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(username) hoặc UNIQUE(email): một yêu cầu khác vừa tạo tài khoản trùng
            throw new ConflictException("Tên đăng nhập hoặc email vừa được tài khoản khác sử dụng!");
        }
        userRoleRepository.saveAndFlush(UserRole.builder()
                .id(new UserRoleId(user.getId(), role.getId()))
                .user(user)
                .role(role)
                .createdAt(now)
                .build());

        if (doctor) {
            DoctorProfileInput profile = request.doctorProfile();
            String doctorPhone = profile.phone() != null && !profile.phone().isBlank() ? profile.phone()
                    : phone != null && phone.length() <= MAX_DOCTOR_PHONE ? phone : null;
            doctorService.createProfileForUser(user.getId(), new DoctorProfileData(user.getFullName(),
                    profile.specialtyId(), doctorPhone, profile.licenseNumber(), profile.bio()));
        } else if ("PATIENT".equals(roleCode)) {
            patientService.createForNewUser(user, RegisterRequest.builder()
                    .username(username)
                    .fullName(user.getFullName())
                    .email(email)
                    .phone(phone)
                    .build());
        }

        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.USER_CREATE,
                AuditActions.ENTITY_USERS, user.getId()).with("role", roleCode));
        return toDTO(user);
    }

    /** Sửa họ tên, điện thoại, email của một tài khoản; đồng bộ sang hồ sơ bệnh nhân/bác sĩ. */
    @Transactional
    public AdminUserDTO update(Long id, UpdateProfileRequest request) {
        currentUserService.requireRole("ADMIN");
        User user = find(id);
        List<String> changed = userService.updateContact(user, request);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.USER_UPDATE,
                AuditActions.ENTITY_USERS, id).with("changedFields", changed));
        return toDTO(user);
    }

    // ================= Khóa/mở và đổi vai trò =================

    @Transactional
    public AdminUserDTO changeStatus(Long id, ChangeUserStatusRequest request) {
        return toDTO(accountSecurityService.changeStatus(id, request.status(), request.reason()));
    }

    /**
     * Đổi vai trò không tự tạo hồ sơ bác sĩ hay bệnh nhân: nếu thêm vai trò DOCTOR cho tài khoản chưa có hồ sơ thì
     * {@code doctorId} vẫn là {@code null} cho tới khi Admin tạo hồ sơ ở trang quản trị bác sĩ.
     */
    @Transactional
    public AdminUserDTO changeRoles(Long id, ChangeUserRolesRequest request) {
        return toDTO(accountSecurityService.changeRoles(id, request.roles()));
    }

    // ================= Chi tiết =================

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng ID: " + id));
    }

    private AdminUserDTO toDTO(User user) {
        return toDTOs(List.of(user)).getFirst();
    }

    /** Tra vai trò và hồ sơ liên quan theo lô: ba truy vấn cho cả trang, không phụ thuộc số dòng. */
    private List<AdminUserDTO> toDTOs(List<User> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, List<String>> roles = new HashMap<>();
        for (Object[] row : userQueryRepository.findRoleCodes(ids)) {
            roles.computeIfAbsent((Long) row[0], key -> new ArrayList<>()).add((String) row[1]);
        }
        Map<Long, Long> patientIds = new HashMap<>();
        userQueryRepository.findPatientIds(ids).forEach(row -> patientIds.put((Long) row[0], (Long) row[1]));
        Map<Long, Long> doctorIds = new HashMap<>();
        userQueryRepository.findDoctorIds(ids).forEach(row -> doctorIds.put((Long) row[0], (Long) row[1]));
        return users.stream()
                .map(user -> AdminUserDTO.from(user, roles.getOrDefault(user.getId(), List.of()),
                        patientIds.get(user.getId()), doctorIds.get(user.getId())))
                .toList();
    }

    private static String oneOf(String field, String value, Set<String> allowed, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new FieldValidationException(field, message);
        }
        return normalized;
    }

    private static Specification<User> matching(String keyword, String role, String status) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (status != null) {
                all.add(cb.equal(root.get("status"), status));
            }
            if (role != null) {
                Subquery<Long> withRole = query.subquery(Long.class);
                Root<UserRole> userRole = withRole.from(UserRole.class);
                withRole.select(userRole.get("user").get("id"))
                        .where(cb.equal(userRole.get("role").get("code"), role));
                all.add(root.get("id").in(withRole));
            }
            if (!SearchTerms.isBlank(keyword)) {
                String pattern = SearchTerms.toLikePattern(keyword);
                all.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern, SearchTerms.ESCAPE),
                        cb.like(cb.lower(root.get("email")), pattern, SearchTerms.ESCAPE),
                        cb.like(cb.lower(root.get("fullName")), pattern, SearchTerms.ESCAPE)));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }
}
