package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.doctor.AdminDoctorRequest;
import com.phenikaa.cse702051.medbook.dto.doctor.DoctorAdminDTO;
import com.phenikaa.cse702051.medbook.dto.doctor.DoctorProfileData;
import com.phenikaa.cse702051.medbook.dto.doctor.DoctorPublicDTO;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Specialty;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.repository.UserRoleRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.PageRequests;
import com.phenikaa.cse702051.medbook.util.SearchTerms;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/**
 * Hồ sơ bác sĩ (YCCN-08, 20, 25).
 *
 * <ul>
 * <li><b>Công khai</b>: chỉ bác sĩ đang hoạt động, tìm theo tên hoặc tên chuyên khoa; không lộ tài khoản, số giấy
 * phép hay số điện thoại.</li>
 * <li><b>Quản trị</b>: Admin tạo hồ sơ cho tài khoản đã có vai trò DOCTOR, sửa và xóa. Bác sĩ đã có lịch hẹn, ca
 * làm việc hoặc lần khám không bị xóa hẳn mà chỉ ngừng hoạt động: các giờ trống còn lại từ hôm nay trở đi được gỡ
 * khỏi lịch, lịch đã đặt giữ nguyên.</li>
 * <li><b>Nội bộ</b>: {@link #createProfileForUser} và {@link #syncContact} cho module tài khoản.</li>
 * </ul>
 */
@Service
public class DoctorService {

    private static final Set<String> PUBLIC_SORTS = Set.of("fullName", "id");
    private static final Sort BY_NAME = Sort.by("fullName").ascending().and(Sort.by("id").ascending());
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String SLOT_AVAILABLE = "AVAILABLE";
    private static final int MAX_FULL_NAME = 100;
    private static final int MAX_PHONE = 20;
    private static final int MAX_LICENSE = 50;
    private static final String DUPLICATE_LICENSE = "Số giấy phép hành nghề đã thuộc về hồ sơ khác";

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AppointmentSlotRepository slotRepository;
    private final SpecialtyService specialtyService;
    private final SlotRetirement slotRetirement;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public DoctorService(
            DoctorRepository doctorRepository,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            AppointmentSlotRepository slotRepository,
            SpecialtyService specialtyService,
            SlotRetirement slotRetirement,
            CurrentUserService currentUserService,
            AuditLogService auditLogService) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.slotRepository = slotRepository;
        this.specialtyService = specialtyService;
        this.slotRetirement = slotRetirement;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    // ================= Công khai =================

    /** {@code sort}: {@code fullName} (mặc định) hoặc {@code id}, có thể kèm {@code ,asc|desc}. */
    @Transactional(readOnly = true)
    public PageResponse<DoctorPublicDTO> searchActive(String keyword, Long specialtyId, int page, int size,
            String sort) {
        Sort order = PageRequests.parseSort(sort, PUBLIC_SORTS, BY_NAME);
        Page<Doctor> result = doctorRepository.findAll(matching(keyword, specialtyId, Boolean.TRUE, false),
                PageRequests.of(page, size, order));
        return PageResponse.from(result, DoctorPublicDTO::from);
    }

    /** Bác sĩ đang hoạt động theo ID; hồ sơ đã ngừng hoạt động cũng trả 404 như không tồn tại. */
    @Transactional(readOnly = true)
    public DoctorPublicDTO getActive(Long id) {
        return doctorRepository.findById(id)
                .filter(doctor -> Boolean.TRUE.equals(doctor.getIsActive()))
                .map(DoctorPublicDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bác sĩ!"));
    }

    // ================= Quản trị =================

    @Transactional(readOnly = true)
    public PageResponse<DoctorAdminDTO> searchForAdmin(String keyword, Long specialtyId, Boolean isActive,
            int page, int size) {
        currentUserService.requireRole("ADMIN");
        Page<Doctor> result = doctorRepository.findAll(matching(keyword, specialtyId, isActive, true),
                PageRequests.of(page, size, BY_NAME));
        List<Doctor> doctors = result.getContent();
        if (doctors.isEmpty()) {
            return PageResponse.from(result, doctor -> DoctorAdminDTO.from(doctor, null, false));
        }
        // Tra tên đăng nhập và lịch sử theo lô: mỗi loại một truy vấn cho cả trang
        Map<Long, String> usernames = new HashMap<>();
        userRepository.findAllById(doctors.stream().map(Doctor::getUserId).toList())
                .forEach(user -> usernames.put(user.getId(), user.getUsername()));
        Set<Long> withHistory = new HashSet<>(
                doctorRepository.findIdsWithHistory(doctors.stream().map(Doctor::getId).toList()));
        return PageResponse.from(result, doctor -> DoctorAdminDTO.from(doctor,
                usernames.get(doctor.getUserId()), withHistory.contains(doctor.getId())));
    }

    @Transactional(readOnly = true)
    public DoctorAdminDTO getForAdmin(Long id) {
        currentUserService.requireRole("ADMIN");
        return toAdminDTO(find(id));
    }

    /** Admin tạo hồ sơ cho một tài khoản đã có vai trò DOCTOR và chưa có hồ sơ. */
    @Transactional
    public DoctorAdminDTO create(AdminDoctorRequest request) {
        currentUserService.requireRole("ADMIN");
        if (request.userId() == null) {
            throw new FieldValidationException("userId", "Phải chọn tài khoản của bác sĩ");
        }
        Doctor doctor = createProfileForUser(request.userId(), new DoctorProfileData(request.fullName(),
                request.specialtyId(), request.phone(), request.licenseNumber(), request.bio()));
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.DOCTOR_CREATE,
                AuditActions.ENTITY_DOCTORS, doctor.getId()).with("userId", doctor.getUserId()));
        return toAdminDTO(doctor);
    }

    @Transactional
    public DoctorAdminDTO update(Long id, AdminDoctorRequest request) {
        currentUserService.requireRole("ADMIN");
        Doctor doctor = find(id);
        Specialty specialty = specialtyService.requireActive(request.specialtyId());
        String license = requireLicense(request.licenseNumber());
        if (doctorRepository.existsByLicenseNumberIgnoreCaseAndIdNot(license, id)) {
            throw new FieldConflictException("licenseNumber", DUPLICATE_LICENSE);
        }
        doctor.setFullName(requireFullName(request.fullName()));
        doctor.setSpecialty(specialty);
        doctor.setPhone(normalizePhone(request.phone()));
        doctor.setLicenseNumber(license);
        doctor.setBio(blankToNull(request.bio()));
        doctor = saveOrConflict(doctor);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.DOCTOR_UPDATE,
                AuditActions.ENTITY_DOCTORS, id));
        return toAdminDTO(doctor);
    }

    /**
     * Xóa hồ sơ bác sĩ.
     *
     * @return rỗng nếu đã xóa hẳn (bác sĩ chưa có lịch sử); hồ sơ ở trạng thái ngừng hoạt động nếu đã có lịch
     *         hẹn, ca làm việc hoặc lần khám
     */
    @Transactional
    public Optional<DoctorAdminDTO> delete(Long id) {
        currentUserService.requireRole("ADMIN");
        Doctor doctor = find(id);
        boolean hasHistory = !doctorRepository.findIdsWithHistory(List.of(id)).isEmpty();
        try {
            if (hasHistory) {
                // Giờ trống từ hôm nay trở đi không còn đặt được; lịch đã đặt (slot BOOKED) giữ nguyên.
                List<AppointmentSlot> openSlots = slotRepository.findByDoctorIdAndStatusAndSlotDateGreaterThanEqual(
                        id, SLOT_AVAILABLE, LocalDate.now());
                int retired = slotRetirement.retire(openSlots);
                doctor.setIsActive(false);
                doctor = doctorRepository.saveAndFlush(doctor);
                auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.DOCTOR_DELETE,
                        AuditActions.ENTITY_DOCTORS, id).with("softDeleted", true).with("retiredSlots", retired));
                return Optional.of(DoctorAdminDTO.from(doctor, usernameOf(doctor), true));
            }
            // Chưa có lịch sử: các slot (nếu có) chắc chắn chưa từng gắn lịch hẹn nên xóa hẳn được
            slotRepository.deleteAll(slotRepository.findByDoctorId(id));
            doctorRepository.delete(doctor);
            doctorRepository.flush();
        } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
            // Có người vừa đặt lịch hoặc tạo ca cho bác sĩ này trong lúc xóa
            throw new ConflictException("Hồ sơ bác sĩ vừa có thay đổi. Vui lòng tải lại và thử lại!");
        }
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.DOCTOR_DELETE,
                AuditActions.ENTITY_DOCTORS, id).with("softDeleted", false));
        return Optional.empty();
    }

    // ================= Nội bộ cho module tài khoản (không kiểm quyền người gọi) =================

    /**
     * Tạo hồ sơ bác sĩ cho một tài khoản. Phải gọi trong giao dịch của người gọi (ví dụ cùng giao dịch tạo tài
     * khoản) để lỗi ở đây hoàn tác cả tài khoản.
     *
     * @throws FieldValidationException tài khoản không tồn tại hoặc chưa có vai trò DOCTOR ({@code userId}); chuyên
     *                                  khoa không tồn tại hoặc đã ngừng sử dụng ({@code specialtyId}); thiếu họ tên
     *                                  hoặc số giấy phép
     * @throws FieldConflictException   tài khoản đã có hồ sơ ({@code userId}); số giấy phép đã thuộc hồ sơ khác
     *                                  ({@code licenseNumber})
     */
    @Transactional
    public Doctor createProfileForUser(Long userId, DoctorProfileData data) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new FieldValidationException("userId", "Tài khoản không tồn tại"));
        if (!userRoleRepository.findRoleCodesByUserId(userId).contains(ROLE_DOCTOR)) {
            throw new FieldValidationException("userId", "Tài khoản chưa có vai trò bác sĩ");
        }
        if (doctorRepository.existsByUserId(userId)) {
            throw new FieldConflictException("userId", "Tài khoản này đã có hồ sơ bác sĩ");
        }
        Specialty specialty = specialtyService.requireActive(data.specialtyId());
        String license = requireLicense(data.licenseNumber());
        if (doctorRepository.existsByLicenseNumberIgnoreCase(license)) {
            throw new FieldConflictException("licenseNumber", DUPLICATE_LICENSE);
        }
        String fullName = data.fullName() == null || data.fullName().isBlank() ? user.getFullName() : data.fullName();
        return saveOrConflict(Doctor.builder()
                .userId(userId)
                .specialty(specialty)
                .fullName(requireFullName(fullName))
                .phone(normalizePhone(data.phone()))
                .licenseNumber(license)
                .bio(blankToNull(data.bio()))
                .isActive(true)
                .build());
    }

    /**
     * Đồng bộ họ tên và điện thoại từ tài khoản sang hồ sơ bác sĩ khi người dùng sửa ở {@code /users/me}. Tài
     * khoản không có hồ sơ bác sĩ thì không làm gì; giá trị rỗng hoặc dài hơn cột của hồ sơ thì giữ nguyên.
     */
    @Transactional
    public void syncContact(Long userId, String fullName, String phone) {
        doctorRepository.findByUserId(userId).ifPresent(doctor -> {
            if (fullName != null && !fullName.isBlank() && fullName.trim().length() <= MAX_FULL_NAME) {
                doctor.setFullName(fullName.trim());
            }
            if (phone != null && !phone.isBlank() && phone.trim().length() <= MAX_PHONE) {
                doctor.setPhone(phone.trim());
            }
            doctorRepository.save(doctor);
        });
    }

    // ================= Chi tiết =================

    /**
     * @param admin {@code true}: tìm thêm theo số giấy phép hành nghề (chỉ dành cho trang quản trị)
     */
    private static Specification<Doctor> matching(String keyword, Long specialtyId, Boolean isActive, boolean admin) {
        return (root, query, cb) -> {
            Join<Doctor, Specialty> specialty = root.join("specialty", JoinType.LEFT);
            List<Predicate> all = new ArrayList<>();
            if (isActive != null) {
                all.add(cb.equal(root.get("isActive"), isActive));
            }
            if (specialtyId != null) {
                all.add(cb.equal(specialty.get("id"), specialtyId));
            }
            if (!SearchTerms.isBlank(keyword)) {
                String pattern = SearchTerms.toLikePattern(keyword);
                List<Predicate> any = new ArrayList<>();
                any.add(cb.like(cb.lower(root.get("fullName")), pattern, SearchTerms.ESCAPE));
                any.add(cb.like(cb.lower(specialty.get("name")), pattern, SearchTerms.ESCAPE));
                if (admin) {
                    any.add(cb.like(cb.lower(root.get("licenseNumber")), pattern, SearchTerms.ESCAPE));
                }
                all.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private Doctor find(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ bác sĩ!"));
    }

    private DoctorAdminDTO toAdminDTO(Doctor doctor) {
        boolean hasHistory = !doctorRepository.findIdsWithHistory(List.of(doctor.getId())).isEmpty();
        return DoctorAdminDTO.from(doctor, usernameOf(doctor), hasHistory);
    }

    private String usernameOf(Doctor doctor) {
        return userRepository.findById(doctor.getUserId()).map(User::getUsername).orElse(null);
    }

    private Doctor saveOrConflict(Doctor doctor) {
        try {
            return doctorRepository.saveAndFlush(doctor);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(user_id) hoặc UNIQUE(license_number): hai yêu cầu tạo/sửa gửi đồng thời
            throw new ConflictException("Hồ sơ bác sĩ bị trùng tài khoản hoặc số giấy phép hành nghề!");
        }
    }

    private static String requireFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new FieldValidationException("fullName", "Họ và tên không được để trống");
        }
        String trimmed = fullName.trim();
        if (trimmed.length() > MAX_FULL_NAME) {
            throw new FieldValidationException("fullName", "Họ và tên tối đa " + MAX_FULL_NAME + " ký tự");
        }
        return trimmed;
    }

    private static String requireLicense(String licenseNumber) {
        if (licenseNumber == null || licenseNumber.isBlank()) {
            throw new FieldValidationException("licenseNumber", "Số giấy phép hành nghề không được để trống");
        }
        String trimmed = licenseNumber.trim();
        if (trimmed.length() > MAX_LICENSE) {
            throw new FieldValidationException("licenseNumber",
                    "Số giấy phép hành nghề tối đa " + MAX_LICENSE + " ký tự");
        }
        return trimmed;
    }

    private static String normalizePhone(String phone) {
        String trimmed = blankToNull(phone);
        if (trimmed != null && trimmed.length() > MAX_PHONE) {
            throw new FieldValidationException("phone", "Số điện thoại tối đa " + MAX_PHONE + " ký tự");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
