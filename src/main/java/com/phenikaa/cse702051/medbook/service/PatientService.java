package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.PatientAdminDTO;
import com.phenikaa.cse702051.medbook.dto.PatientDTO;
import com.phenikaa.cse702051.medbook.dto.PatientUpdateRequest;
import com.phenikaa.cse702051.medbook.dto.RegisterRequest;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Hồ sơ bệnh nhân (YCCN-01, 07).
 *
 * <ul>
 * <li>Bệnh nhân chỉ xem/sửa hồ sơ của chính mình.</li>
 * <li>Bác sĩ chỉ thấy bệnh nhân thuộc phạm vi phụ trách ({@link DoctorScopePolicy}).</li>
 * <li>Admin chỉ thấy trường hành chính ({@link PatientAdminDTO}).</li>
 * </ul>
 */
@Service
public class PatientService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PatientRepository patientRepository;
    private final MedicalRecordService medicalRecordService;
    private final CurrentUserService currentUserService;
    private final CurrentActorService currentActorService;
    private final DoctorScopePolicy doctorScopePolicy;
    private final AuditLogService auditLogService;

    public PatientService(
            PatientRepository patientRepository,
            MedicalRecordService medicalRecordService,
            CurrentUserService currentUserService,
            CurrentActorService currentActorService,
            DoctorScopePolicy doctorScopePolicy,
            AuditLogService auditLogService) {
        this.patientRepository = patientRepository;
        this.medicalRecordService = medicalRecordService;
        this.currentUserService = currentUserService;
        this.currentActorService = currentActorService;
        this.doctorScopePolicy = doctorScopePolicy;
        this.auditLogService = auditLogService;
    }

    /**
     * Tạo hồ sơ bệnh nhân + bệnh án rỗng cho tài khoản vừa đăng ký. Phải gọi trong cùng giao
     * dịch với việc tạo {@code User} để lỗi bất kỳ làm rollback cả tài khoản (không để lại
     * tài khoản mồ côi).
     */
    @Transactional
    public Patient createForNewUser(User user, RegisterRequest request) {
        LocalDateTime now = LocalDateTime.now();

        Patient patient = Patient.builder()
                .user(user)
                // mã tạm duy nhất; đổi thành BN + id ngay bên dưới
                .patientCode("TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .fullName(user.getFullName())
                .dateOfBirth(request.getDateOfBirth() != null ? request.getDateOfBirth() : LocalDate.of(2000, 1, 1))
                .genderCode(request.getGenderCode() != null && !request.getGenderCode().isBlank()
                        ? request.getGenderCode().trim().toUpperCase(Locale.ROOT)
                        : "OTHER")
                .phone(user.getPhone() != null ? user.getPhone() : "0000000000")
                .email(user.getEmail())
                .address(request.getAddress())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .bloodType(request.getBloodType())
                .allergies(request.getAllergies())
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();
        patient = patientRepository.save(patient);
        patient.setPatientCode(String.format("BN%06d", patient.getId()));
        patient = patientRepository.save(patient);

        medicalRecordService.createForPatient(patient);
        return patient;
    }

    /**
     * Đồng bộ họ tên, điện thoại, email từ tài khoản sang hồ sơ bệnh nhân khi người dùng sửa ở {@code /users/me}
     * hoặc Admin sửa tài khoản. Gọi trong cùng giao dịch với việc lưu {@code User}; tài khoản không có hồ sơ bệnh
     * nhân thì không làm gì. Không kiểm quyền người gọi (API nội bộ cho module tài khoản). Giá trị {@code null}
     * hoặc rỗng nghĩa là giữ nguyên, vì hai cột họ tên và điện thoại của bệnh nhân không được để trống.
     */
    @Transactional
    public void syncContactFromUser(Long userId, String fullName, String phone, String email) {
        patientRepository.findByUserId(userId).ifPresent(patient -> {
            if (fullName != null && !fullName.isBlank()) {
                patient.setFullName(fullName.trim());
            }
            if (phone != null && !phone.isBlank()) {
                patient.setPhone(phone.trim());
            }
            if (email != null && !email.isBlank()) {
                patient.setEmail(email.trim());
            }
            patient.setUpdatedAt(LocalDateTime.now());
            patientRepository.save(patient);
        });
    }

    @Transactional(readOnly = true)
    public PatientDTO getCurrentPatient() {
        currentUserService.requireRole("PATIENT");
        return PatientDTO.from(currentPatient());
    }

    @Transactional
    public PatientDTO updateCurrentPatient(PatientUpdateRequest updateRequest) {
        currentUserService.requireRole("PATIENT");
        Patient patient = currentPatient();
        applyUpdate(patient, updateRequest);
        patient.setUpdatedAt(LocalDateTime.now());
        return PatientDTO.from(patientRepository.save(patient));
    }

    /**
     * Danh sách bệnh nhân. Bác sĩ → chỉ bệnh nhân trong phạm vi phụ trách, đầy đủ thông tin lâm sàng
     * cơ bản ({@link PatientDTO}); Admin → mọi bệnh nhân nhưng chỉ trường hành chính.
     */
    @Transactional(readOnly = true)
    public PageResponse<?> search(String keyword, int page, int size) {
        CurrentUser user = currentUserService.requireRole("DOCTOR", "ADMIN");
        // khóa chính làm tiêu chí phụ: bệnh nhân trùng họ tên không bị lặp hoặc bỏ sót giữa các trang
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("fullName").ascending().and(Sort.by("id").ascending()));
        String pattern = toPattern(keyword);

        if (user.hasRole("DOCTOR")) {
            Long doctorId = currentActorService.requireCurrentDoctorId();
            Page<Patient> result = patientRepository.searchForDoctor(
                    doctorId, pattern, AppointmentStatus.CANCELLED, pageable);
            return PageResponse.from(result, PatientDTO::from);
        }
        return PageResponse.from(patientRepository.searchAll(pattern, pageable), PatientAdminDTO::from);
    }

    /**
     * Chi tiết một bệnh nhân. Bác sĩ phải phụ trách bệnh nhân (ngược lại 403 + audit
     * ACCESS_DENIED) và lần đọc được ghi audit PATIENT_VIEW; Admin nhận trường hành chính.
     */
    @Transactional
    public Object getById(Long id) {
        CurrentUser user = currentUserService.requireRole("DOCTOR", "ADMIN");
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bệnh nhân ID: " + id));

        if (user.hasRole("DOCTOR")) {
            Long doctorId = currentActorService.requireCurrentDoctorId();
            if (!doctorScopePolicy.isResponsible(doctorId, patient.getId())) {
                auditLogService.recordAccessDenied(AuditActions.ENTITY_PATIENTS, id,
                        "Bác sĩ không phụ trách bệnh nhân");
                throw new ForbiddenException("Bạn không phụ trách bệnh nhân này!");
            }
            auditLogService.record(AuditEvent.of(AuditActions.PATIENT_VIEW, AuditActions.ENTITY_PATIENTS, id));
            return PatientDTO.from(patient);
        }
        return PatientAdminDTO.from(patient);
    }

    private Patient currentPatient() {
        Long patientId = currentActorService.requireCurrentPatientId();
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ bệnh nhân của tài khoản hiện tại!"));
    }

    private void applyUpdate(Patient patient, PatientUpdateRequest request) {
        if (request.fullName() != null) {
            patient.setFullName(request.fullName().trim());
        }
        if (request.dateOfBirth() != null) {
            patient.setDateOfBirth(request.dateOfBirth());
        }
        if (request.genderCode() != null) {
            patient.setGenderCode(request.genderCode());
        }
        if (request.phone() != null) {
            patient.setPhone(request.phone().trim());
        }
        if (request.email() != null) {
            patient.setEmail(request.email().trim());
        }
        if (request.address() != null) {
            patient.setAddress(request.address().trim());
        }
        if (request.emergencyContactName() != null) {
            patient.setEmergencyContactName(request.emergencyContactName().trim());
        }
        if (request.emergencyContactPhone() != null) {
            patient.setEmergencyContactPhone(request.emergencyContactPhone().trim());
        }
        if (request.bloodType() != null) {
            patient.setBloodType(request.bloodType().toUpperCase(Locale.ROOT));
        }
        if (request.allergies() != null) {
            patient.setAllergies(request.allergies());
        }
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** Chuỗi tìm kiếm không phân biệt hoa thường; ký tự đại diện của người dùng bị vô hiệu hóa. */
    private static String toPattern(String keyword) {
        String cleaned = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        cleaned = cleaned.replace("%", "").replace("_", "");
        return "%" + cleaned + "%";
    }
}
