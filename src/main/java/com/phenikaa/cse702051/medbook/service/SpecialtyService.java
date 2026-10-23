package com.phenikaa.cse702051.medbook.service;

import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.config.CacheConfig;
import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.catalog.SpecialtyAdminDTO;
import com.phenikaa.cse702051.medbook.dto.catalog.SpecialtyDTO;
import com.phenikaa.cse702051.medbook.dto.catalog.SpecialtyRequest;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Specialty;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.SpecialtyRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.CatalogRules;
import com.phenikaa.cse702051.medbook.util.PageRequests;

/**
 * Danh mục chuyên khoa (YCCN-20, 25). Khách chỉ thấy chuyên khoa {@code ACTIVE}; Admin tạo, sửa, xóa. Mã bất biến
 * sau khi tạo. Xóa chuyên khoa đang được hồ sơ bác sĩ dùng chỉ chuyển sang {@code INACTIVE} để không làm hỏng dữ
 * liệu đang tham chiếu.
 */
@Service
public class SpecialtyService {

    private static final Sort BY_NAME = Sort.by("name").ascending().and(Sort.by("id").ascending());
    private static final String DUPLICATE_CODE = "Mã chuyên khoa đã được sử dụng";

    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final PublicCatalogCache publicCatalogCache;

    public SpecialtyService(
            SpecialtyRepository specialtyRepository,
            DoctorRepository doctorRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            PublicCatalogCache publicCatalogCache) {
        this.specialtyRepository = specialtyRepository;
        this.doctorRepository = doctorRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.publicCatalogCache = publicCatalogCache;
    }

    // ================= Công khai =================

    /**
     * Trang đầu không kèm từ khóa (trường hợp mọi màn hình chọn danh mục đều gọi) được lấy từ bộ nhớ đệm; các
     * truy vấn khác luôn đọc CSDL.
     */
    @Cacheable(cacheNames = CacheConfig.PUBLIC_SPECIALTIES, key = "#size",
            condition = CacheConfig.FIRST_PAGE_WITHOUT_KEYWORD)
    @Transactional(readOnly = true)
    public PageResponse<SpecialtyDTO> listActive(String keyword, int page, int size) {
        Page<Specialty> result = specialtyRepository.findAll(
                CatalogRules.matching(CatalogRules.ACTIVE, keyword, "name", "code"),
                PageRequests.of(page, size, BY_NAME));
        return PageResponse.from(result, SpecialtyDTO::from);
    }

    /** Chuyên khoa đang hoạt động theo ID; bản đã ngừng sử dụng cũng trả 404 như không tồn tại. */
    @Transactional(readOnly = true)
    public SpecialtyDTO getActive(Long id) {
        return specialtyRepository.findById(id)
                .filter(specialty -> CatalogRules.isActive(specialty.getStatus()))
                .map(SpecialtyDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên khoa!"));
    }

    // ================= Quản trị =================

    @Transactional(readOnly = true)
    public PageResponse<SpecialtyAdminDTO> listForAdmin(String keyword, String status, int page, int size) {
        currentUserService.requireRole("ADMIN");
        Page<Specialty> result = specialtyRepository.findAll(
                CatalogRules.matching(CatalogRules.statusFilter(status), keyword, "name", "code"),
                PageRequests.of(page, size, BY_NAME));
        return PageResponse.from(result, SpecialtyAdminDTO::from);
    }

    @Transactional(readOnly = true)
    public SpecialtyAdminDTO getForAdmin(Long id) {
        currentUserService.requireRole("ADMIN");
        return SpecialtyAdminDTO.from(find(id));
    }

    @Transactional
    public SpecialtyAdminDTO create(SpecialtyRequest request) {
        currentUserService.requireRole("ADMIN");
        publicCatalogCache.evictAfterCommit(CacheConfig.PUBLIC_SPECIALTIES);
        String code = CatalogRules.requireCode(request.code());
        if (specialtyRepository.existsByCodeIgnoreCase(code)) {
            throw new FieldConflictException("code", DUPLICATE_CODE);
        }
        Specialty specialty = Specialty.builder()
                .code(code)
                .name(request.name().trim())
                .description(CatalogRules.blankToNull(request.description()))
                .status(CatalogRules.statusOrDefault(request.status(), CatalogRules.ACTIVE))
                .build();
        try {
            specialty = specialtyRepository.saveAndFlush(specialty);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(code): hai yêu cầu tạo cùng mã gửi đồng thời
            throw new FieldConflictException("code", DUPLICATE_CODE);
        }
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.CATALOG_CREATE,
                AuditActions.ENTITY_SPECIALTIES, specialty.getId()).with("code", code));
        return SpecialtyAdminDTO.from(specialty);
    }

    @Transactional
    public SpecialtyAdminDTO update(Long id, SpecialtyRequest request) {
        currentUserService.requireRole("ADMIN");
        publicCatalogCache.evictAfterCommit(CacheConfig.PUBLIC_SPECIALTIES);
        Specialty specialty = find(id);
        CatalogRules.assertCodeUnchanged(request.code(), specialty.getCode());
        specialty.setName(request.name().trim());
        specialty.setDescription(CatalogRules.blankToNull(request.description()));
        specialty.setStatus(CatalogRules.statusOrDefault(request.status(), specialty.getStatus()));
        specialty = specialtyRepository.saveAndFlush(specialty);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.CATALOG_UPDATE,
                AuditActions.ENTITY_SPECIALTIES, id).with("code", specialty.getCode()));
        return SpecialtyAdminDTO.from(specialty);
    }

    /**
     * Xóa chuyên khoa.
     *
     * @return rỗng nếu đã xóa hẳn; bản ghi ở trạng thái {@code INACTIVE} nếu còn hồ sơ bác sĩ tham chiếu
     */
    @Transactional
    public Optional<SpecialtyAdminDTO> delete(Long id) {
        currentUserService.requireRole("ADMIN");
        publicCatalogCache.evictAfterCommit(CacheConfig.PUBLIC_SPECIALTIES);
        Specialty specialty = find(id);
        if (doctorRepository.existsBySpecialtyId(id)) {
            specialty.setStatus(CatalogRules.INACTIVE);
            specialty = specialtyRepository.saveAndFlush(specialty);
            auditDelete(specialty, true);
            return Optional.of(SpecialtyAdminDTO.from(specialty));
        }
        try {
            specialtyRepository.delete(specialty);
            specialtyRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // Khóa ngoại doctors.specialty_id: vừa có hồ sơ bác sĩ được gán chuyên khoa này
            throw new ConflictException("Chuyên khoa vừa được sử dụng ở nơi khác. Vui lòng tải lại và thử lại!");
        }
        auditDelete(specialty, false);
        return Optional.empty();
    }

    // ================= Nội bộ cho module khác (không kiểm quyền người gọi) =================

    /**
     * Chuyên khoa đang hoạt động để gán cho hồ sơ bác sĩ; không tồn tại hoặc đã ngừng sử dụng → 400 ở trường
     * {@code specialtyId}.
     */
    @Transactional(readOnly = true)
    public Specialty requireActive(Long id) {
        if (id == null) {
            throw new FieldValidationException("specialtyId", "Phải chọn chuyên khoa");
        }
        return specialtyRepository.findById(id)
                .filter(specialty -> CatalogRules.isActive(specialty.getStatus()))
                .orElseThrow(() -> new FieldValidationException("specialtyId",
                        "Chuyên khoa không tồn tại hoặc đã ngừng sử dụng"));
    }

    private Specialty find(Long id) {
        return specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên khoa!"));
    }

    private void auditDelete(Specialty specialty, boolean softDeleted) {
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.CATALOG_DELETE,
                AuditActions.ENTITY_SPECIALTIES, specialty.getId())
                .with("code", specialty.getCode())
                .with("softDeleted", softDeleted));
    }
}
