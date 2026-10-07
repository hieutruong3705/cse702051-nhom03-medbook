package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.user.UpdateProfileRequest;
import com.phenikaa.cse702051.medbook.dto.user.UserProfileDTO;
import com.phenikaa.cse702051.medbook.exception.FieldConflictException;
import com.phenikaa.cse702051.medbook.exception.UnauthorizedException;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.UserQueryRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Hồ sơ tài khoản của người đang đăng nhập ({@code /users/me}), dùng được cho cả ba vai trò. Người dùng lấy từ
 * JWT; chỉ sửa được họ tên, điện thoại, email (không đổi được tên đăng nhập, vai trò, trạng thái). Thay đổi được
 * đồng bộ sang hồ sơ bệnh nhân hoặc bác sĩ trong cùng giao dịch và không làm mất phiên đăng nhập hiện tại.
 */
@Service
public class UserService {

    private static final String DUPLICATE_EMAIL = "Email đã được tài khoản khác sử dụng";

    private final UserRepository userRepository;
    private final UserQueryRepository userQueryRepository;
    private final PatientService patientService;
    private final DoctorService doctorService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public UserService(
            UserRepository userRepository,
            UserQueryRepository userQueryRepository,
            PatientService patientService,
            DoctorService doctorService,
            CurrentUserService currentUserService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.userQueryRepository = userQueryRepository;
        this.patientService = patientService;
        this.doctorService = doctorService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getMe() {
        return profileOf(currentUser());
    }

    @Transactional
    public UserProfileDTO updateMe(UpdateProfileRequest request) {
        User user = currentUser();
        List<String> changed = updateContact(user, request);
        if (!changed.isEmpty()) {
            auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.USER_UPDATE,
                    AuditActions.ENTITY_USERS, user.getId()).with("changedFields", changed));
        }
        return profileOf(user);
    }

    /**
     * Cập nhật họ tên, điện thoại, email của một tài khoản và đồng bộ sang hồ sơ bệnh nhân/bác sĩ. Dùng chung cho
     * {@code /users/me} và trang quản trị; phải gọi trong giao dịch của người gọi. Không kiểm quyền người gọi.
     *
     * @return tên các trường đã thay đổi (rỗng nếu không có gì đổi)
     * @throws FieldConflictException email đã thuộc tài khoản khác (so sánh không phân biệt hoa thường)
     */
    @Transactional
    public List<String> updateContact(User user, UpdateProfileRequest request) {
        String fullName = request.fullName().trim();
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        List<String> changed = new ArrayList<>();
        if (!email.equalsIgnoreCase(user.getEmail())) {
            if (userQueryRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
                throw new FieldConflictException("email", DUPLICATE_EMAIL);
            }
            user.setEmail(email);
            changed.add("email");
        }
        if (!fullName.equals(user.getFullName())) {
            user.setFullName(fullName);
            changed.add("fullName");
        }
        if (!Objects.equals(phone, user.getPhone())) {
            user.setPhone(phone);
            changed.add("phone");
        }
        if (changed.isEmpty()) {
            return changed;
        }
        user.setUpdatedAt(LocalDateTime.now());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(email): một yêu cầu khác vừa dùng email này
            throw new FieldConflictException("email", DUPLICATE_EMAIL);
        }
        patientService.syncContactFromUser(user.getId(), fullName, phone, email);
        doctorService.syncContact(user.getId(), fullName, phone);
        return changed;
    }

    private User currentUser() {
        return userRepository.findById(currentUserService.requireUserId())
                .orElseThrow(() -> new UnauthorizedException("Phiên đăng nhập không còn hợp lệ!"));
    }

    private UserProfileDTO profileOf(User user) {
        List<Long> ids = List.of(user.getId());
        List<String> roles = userQueryRepository.findRoleCodes(ids).stream().map(row -> (String) row[1]).toList();
        Long patientId = userQueryRepository.findPatientIds(ids).stream()
                .map(row -> (Long) row[1]).findFirst().orElse(null);
        Long doctorId = userQueryRepository.findDoctorIds(ids).stream()
                .map(row -> (Long) row[1]).findFirst().orElse(null);
        return UserProfileDTO.from(user, roles, patientId, doctorId);
    }
}
