package com.phenikaa.cse702051.medbook.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.config.JwtUtil;
import com.phenikaa.cse702051.medbook.config.JwtUtil.IssuedToken;
import com.phenikaa.cse702051.medbook.dto.ChangePasswordRequest;
import com.phenikaa.cse702051.medbook.dto.LoginRequest;
import com.phenikaa.cse702051.medbook.dto.LoginResponse;
import com.phenikaa.cse702051.medbook.dto.RegisterRequest;
import com.phenikaa.cse702051.medbook.dto.RegisterResponse;
import com.phenikaa.cse702051.medbook.exception.AccountLockedException;
import com.phenikaa.cse702051.medbook.exception.ApiException;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.TooManyRequestsException;
import com.phenikaa.cse702051.medbook.exception.UnauthorizedException;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.model.Role;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.model.UserRole;
import com.phenikaa.cse702051.medbook.model.UserRoleId;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.RoleRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.repository.UserRoleRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.security.LoginRateLimiter;

/**
 * Đăng ký, đăng nhập, đổi mật khẩu (YCCN-01, 02, 04).
 *
 * <ul>
 * <li>Mật khẩu băm BCrypt (cost do {@code PasswordEncoder} cấu hình, hiện 12); không trả hash.</li>
 * <li>Đăng nhập sai {@code max-failed-logins} lần liên tiếp → khóa {@code lock-minutes} phút.
 * Thông báo sai tên/mật khẩu luôn giống nhau để không lộ tài khoản có tồn tại hay không.</li>
 * <li>Một địa chỉ IP đăng nhập sai quá nhiều lần trong thời gian ngắn (trên bất kỳ tài khoản nào) → 429,
 * xem {@link LoginRateLimiter}.</li>
 * <li>Mỗi JWT mang {@code jti} và {@code ver} (xem {@link SessionService}); đổi mật khẩu tăng
 * {@code token_version} nên mọi phiên cũ bị vô hiệu.</li>
 * <li>Đăng nhập thành công/thất bại/khóa đều ghi audit.</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Tên đăng nhập/email hoặc mật khẩu không chính xác!";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PatientService patientService;
    private final PasswordEncoder passwordEncoder;
    private final LoginSessionService loginSessions;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final LoginRateLimiter loginRateLimiter;
    private final int maxFailedLogins;
    private final int lockMinutes;

    private volatile String dummyHash;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            PatientService patientService,
            PasswordEncoder passwordEncoder,
            LoginSessionService loginSessions,
            AuditLogService auditLogService,
            CurrentUserService currentUserService,
            LoginRateLimiter loginRateLimiter,
            @Value("${medbook.security.max-failed-logins:5}") int maxFailedLogins,
            @Value("${medbook.security.lock-minutes:15}") int lockMinutes) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.patientService = patientService;
        this.passwordEncoder = passwordEncoder;
        this.loginSessions = loginSessions;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.loginRateLimiter = loginRateLimiter;
        this.maxFailedLogins = maxFailedLogins;
        this.lockMinutes = lockMinutes;
    }

    /**
     * YCCN-01: Đăng ký tài khoản Bệnh nhân mới. Luôn gán role PATIENT (client không chọn được
     * role). Tạo User + gán role + hồ sơ bệnh nhân + bệnh án trong MỘT giao dịch.
     */
    @Transactional
    public RegisterResponse registerPatient(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Tên đăng nhập '" + username + "' đã được sử dụng!");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email '" + email + "' đã được đăng ký tài khoản khác!");
        }

        LocalDateTime now = LocalDateTime.now();
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .username(username)
                .passwordHash(encodedPassword)
                .fullName(request.getFullName().trim())
                .email(email)
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();
        user = userRepository.save(user);

        Role patientRole = roleRepository.findByCode("PATIENT")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .code("PATIENT")
                        .name("Bệnh nhân")
                        .description("Người bệnh đăng ký khám")
                        .createdAt(now)
                        .build()));

        UserRole userRole = UserRole.builder()
                .id(new UserRoleId(user.getId(), patientRole.getId()))
                .user(user)
                .role(patientRole)
                .createdAt(now)
                .build();
        userRoleRepository.save(userRole);

        // Tạo Patient + MedicalRecord rỗng trong cùng giao dịch với User (lỗi bất kỳ ⇒ rollback cả tài khoản).
        Patient patient = patientService.createForNewUser(user, request);

        return RegisterResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role("PATIENT")
                .patientId(patient.getId())
                .patientCode(patient.getPatientCode())
                .message("Đăng ký tài khoản bệnh nhân thành công!")
                .build();
    }

    /**
     * YCCN-02: Đăng nhập. Không rollback khi ném {@link ApiException} vì phải lưu bộ đếm đăng
     * nhập sai / thời điểm khóa ngay cả khi đăng nhập thất bại.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest request) {
        String input = request.getUsernameOrEmail().trim();
        LocalDateTime now = LocalDateTime.now();

        // IP này vừa đăng nhập sai quá nhiều lần: từ chối ngay, không tra tài khoản và không xét mật khẩu.
        long retryAfter = loginRateLimiter.retryAfterSeconds(loginRateLimiter.currentClient());
        if (retryAfter > 0) {
            throw new TooManyRequestsException("Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau "
                    + Math.max(1, (retryAfter + 59) / 60) + " phút!", retryAfter);
        }

        Optional<User> found = userRepository.findByUsername(input)
                .or(() -> userRepository.findByEmail(input.toLowerCase(Locale.ROOT)));

        if (found.isEmpty()) {
            // Vẫn tốn thời gian băm như trường hợp có tài khoản để không lộ qua độ trễ phản hồi.
            passwordEncoder.matches(request.getPassword(), dummyHash());
            auditLoginFailure(null, input, "USER_NOT_FOUND");
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        User user = found.get();

        // Đang bị khóa tạm do đăng nhập sai quá số lần: từ chối, không xét mật khẩu, không gia hạn khóa.
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            auditLoginFailure(user, input, "TEMPORARILY_LOCKED");
            throw new AccountLockedException(lockedMessage(user.getLockedUntil(), now), user.getLockedUntil());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw failedAttempt(user, input, now);
        }

        // Mật khẩu đúng nhưng Admin đã khóa tài khoản.
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            auditLoginFailure(user, input, "ACCOUNT_DISABLED");
            throw new AccountLockedException(
                    "Tài khoản '" + user.getUsername() + "' đã bị khóa. Vui lòng liên hệ quản trị viên!", null);
        }

        List<String> roleCodes = userRoleRepository.findByUserIdWithRole(user.getId()).stream()
                .map(userRole -> userRole.getRole().getCode())
                .toList();
        if (roleCodes.isEmpty()) {
            auditLoginFailure(user, input, "NO_ROLE");
            throw new ForbiddenException("Tài khoản chưa được gán vai trò nào. Vui lòng liên hệ quản trị viên!");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(now);
        userRepository.save(user);

        Long patientId = patientRepository.findByUserId(user.getId()).map(Patient::getId).orElse(null);
        Long doctorId = doctorRepository.findByUserId(user.getId()).map(Doctor::getId).orElse(null);

        var issued = loginSessions.createTokens(user.getId());

        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.LOGIN_SUCCESS, AuditActions.ENTITY_USERS, user.getId())
                .byActor(user.getId())
                .with("username", user.getUsername())
                .with("roles", roleCodes));

        return LoginResponse.builder()
                .token(issued.token())
                .tokenType("Bearer")
                .expiresAt(issued.expiresAt())
                .refreshToken(issued.refreshToken())
                .refreshExpiresAt(issued.refreshExpiresAt())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roleCodes)
                .patientId(patientId)
                .doctorId(doctorId)
                .message("Đăng nhập thành công!")
                .build();
    }

    /**
     * YCCN-04: Đổi mật khẩu của người đang đăng nhập. Mật khẩu cũ sai → 400 (không phải 401 để giao
     * diện không hiểu nhầm là hết phiên). Thành công → mọi token đã cấp (kể cả token hiện tại) bị vô
     * hiệu, người dùng phải đăng nhập lại.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        CurrentUser current = currentUserService.requireCurrentUser();
        User user = userRepository.findByIdForUpdate(current.userId())
                .orElseThrow(() -> new UnauthorizedException("Phiên đăng nhập không còn hợp lệ!"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new FieldValidationException("oldPassword", "Mật khẩu cũ không đúng");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new FieldValidationException("newPassword", "Mật khẩu mới phải khác mật khẩu cũ");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.PASSWORD_CHANGED, AuditActions.ENTITY_USERS, user.getId())
                .byActor(user.getId()));
    }

    // ---------- nội bộ ----------

    /** Ghi nhận một lần sai mật khẩu; đủ số lần thì khóa tạm. Luôn trả về ngoại lệ để ném. */
    private ApiException failedAttempt(User user, String input, LocalDateTime now) {
        int failed = user.getFailedLoginCount() + 1;
        user.setUpdatedAt(now);

        if (failed >= maxFailedLogins) {
            LocalDateTime until = now.plusMinutes(lockMinutes);
            user.setLockedUntil(until);
            user.setFailedLoginCount(0); // hết khóa sẽ có lại đủ số lần thử
            userRepository.save(user);

            auditLoginFailure(user, input, "WRONG_PASSWORD");
            auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.ACCOUNT_LOCKED, AuditActions.ENTITY_USERS, user.getId())
                    .with("username", user.getUsername())
                    .with("lockedUntil", until.toString())
                    .with("lockMinutes", lockMinutes));
            return new AccountLockedException(lockedMessage(until, now), until);
        }

        user.setFailedLoginCount(failed);
        userRepository.save(user);
        auditLoginFailure(user, input, "WRONG_PASSWORD");
        return new UnauthorizedException(INVALID_CREDENTIALS);
    }

    private void auditLoginFailure(User user, String input, String reason) {
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.LOGIN_FAILED, AuditActions.ENTITY_USERS,
                user == null ? null : user.getId())
                .with("username", input)
                .with("reason", reason));
        if (loginRateLimiter.recordFailure(loginRateLimiter.currentClient())) {
            // ghi một lần cho mỗi đợt chạm ngưỡng; các lần bị 429 sau đó không ghi thêm để nhật ký không bị làm ngập
            auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.LOGIN_RATE_LIMITED,
                    AuditActions.ENTITY_USERS, user == null ? null : user.getId())
                    .with("username", input));
        }
    }

    private String lockedMessage(LocalDateTime until, LocalDateTime now) {
        long minutes = Math.max(1, Duration.between(now, until).plusSeconds(59).toMinutes());
        return "Tài khoản tạm thời bị khóa do đăng nhập sai nhiều lần. Vui lòng thử lại sau " + minutes + " phút!";
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode("medbook-timing-equalizer");
            dummyHash = hash;
        }
        return hash;
    }
}
