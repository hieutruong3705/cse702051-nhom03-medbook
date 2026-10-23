package com.phenikaa.cse702051.medbook.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.catalog.MedicineAdminDTO;
import com.phenikaa.cse702051.medbook.dto.catalog.MedicineDTO;
import com.phenikaa.cse702051.medbook.dto.catalog.MedicineRequest;
import com.phenikaa.cse702051.medbook.exception.FieldConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Medicine;
import com.phenikaa.cse702051.medbook.repository.MedicineRepository;
import com.phenikaa.cse702051.medbook.repository.PrescriptionItemRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.CatalogRules;
import com.phenikaa.cse702051.medbook.util.PageRequests;

/**
 * Danh mục thuốc (YCCN-20). Bác sĩ tra thuốc {@code ACTIVE} để kê đơn; Admin tạo, sửa, xóa và thấy cả thuốc đã
 * ngừng sử dụng. Bệnh nhân không truy cập danh mục này. Thuốc đã được kê trong đơn không bị xóa hẳn mà chuyển
 * sang {@code INACTIVE}.
 */
@Service
public class MedicineService {

    private static final Sort BY_NAME = Sort.by("name").ascending().and(Sort.by("id").ascending());
    private static final String DUPLICATE_CODE = "Mã thuốc đã được sử dụng";

    private final MedicineRepository medicineRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public MedicineService(
            MedicineRepository medicineRepository,
            PrescriptionItemRepository prescriptionItemRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService) {
        this.medicineRepository = medicineRepository;
        this.prescriptionItemRepository = prescriptionItemRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    // ================= Bác sĩ và Admin =================

    /** Bác sĩ luôn chỉ thấy thuốc đang hoạt động; Admin thấy tất cả và lọc được theo {@code status}. */
    @Transactional(readOnly = true)
    public PageResponse<MedicineDTO> list(String keyword, String status, int page, int size) {
        CurrentUser user = currentUserService.requireRole("DOCTOR", "ADMIN");
        String statusFilter = user.hasRole("ADMIN") ? CatalogRules.statusFilter(status) : CatalogRules.ACTIVE;
        Page<Medicine> result = medicineRepository.findAll(
                CatalogRules.matching(statusFilter, keyword, "name", "code"),
                PageRequests.of(page, size, BY_NAME));
        return PageResponse.from(result, MedicineDTO::from);
    }

    // ================= Quản trị =================

    @Transactional(readOnly = true)
    public PageResponse<MedicineAdminDTO> listForAdmin(String keyword, String status, int page, int size) {
        currentUserService.requireRole("ADMIN");
        Page<Medicine> result = medicineRepository.findAll(
                CatalogRules.matching(CatalogRules.statusFilter(status), keyword, "name", "code"),
                PageRequests.of(page, size, BY_NAME));
        return PageResponse.from(result, MedicineAdminDTO::from);
    }

    @Transactional(readOnly = true)
    public MedicineAdminDTO getForAdmin(Long id) {
        currentUserService.requireRole("ADMIN");
        return MedicineAdminDTO.from(find(id));
    }

    @Transactional
    public MedicineAdminDTO create(MedicineRequest request) {
        currentUserService.requireRole("ADMIN");
        String code = CatalogRules.requireCode(request.code());
        if (medicineRepository.existsByCodeIgnoreCase(code)) {
            throw new FieldConflictException("code", DUPLICATE_CODE);
        }
        Medicine medicine = Medicine.builder()
                .code(code)
                .name(request.name().trim())
                .unit(CatalogRules.blankToNull(request.unit()))
                .description(CatalogRules.blankToNull(request.description()))
                .status(CatalogRules.statusOrDefault(request.status(), CatalogRules.ACTIVE))
                .build();
        try {
            medicine = medicineRepository.saveAndFlush(medicine);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(code): hai yêu cầu tạo cùng mã gửi đồng thời
            throw new FieldConflictException("code", DUPLICATE_CODE);
        }
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.CATALOG_CREATE,
                AuditActions.ENTITY_MEDICINES, medicine.getId()).with("code", code));
        return MedicineAdminDTO.from(medicine);
    }

    @Transactional
    public MedicineAdminDTO update(Long id, MedicineRequest request) {
        currentUserService.requireRole("ADMIN");
        Medicine medicine = find(id);
        CatalogRules.assertCodeUnchanged(request.code(), medicine.getCode());
        medicine.setName(request.name().trim());
        medicine.setUnit(CatalogRules.blankToNull(request.unit()));
        medicine.setDescription(CatalogRules.blankToNull(request.description()));
        medicine.setStatus(CatalogRules.statusOrDefault(request.status(), medicine.getStatus()));
        medicine = medicineRepository.saveAndFlush(medicine);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.CATALOG_UPDATE,
                AuditActions.ENTITY_MEDICINES, id).with("code", medicine.getCode()));
        return MedicineAdminDTO.from(medicine);
    }

    /**
     * Xóa thuốc.
     *
     * @return rỗng nếu đã xóa hẳn; bản ghi ở trạng thái {@code INACTIVE} nếu thuốc đã được kê trong đơn nào đó
     */
    @Transactional
    public Optional<MedicineAdminDTO> delete(Long id) {
        currentUserService.requireRole("ADMIN");
        Medicine medicine = find(id);
        if (prescriptionItemRepository.existsByMedicineId(id)) {
            medicine.setStatus(CatalogRules.INACTIVE);
            medicine = medicineRepository.saveAndFlush(medicine);
            auditDelete(medicine, true);
            return Optional.of(MedicineAdminDTO.from(medicine));
        }
        medicineRepository.delete(medicine);
        auditDelete(medicine, false);
        return Optional.empty();
    }

    // ================= Nội bộ cho module khác (không kiểm quyền người gọi) =================

    /**
     * Thuốc đang hoạt động để kê đơn. Không tồn tại hoặc đã ngừng sử dụng → 400 ở trường {@code medicineId};
     * không bao giờ trả {@code null}.
     */
    @Transactional(readOnly = true)
    public Medicine requireActive(Long id) {
        if (id == null) {
            throw new FieldValidationException("medicineId", "Phải chọn thuốc");
        }
        return medicineRepository.findById(id)
                .filter(medicine -> CatalogRules.isActive(medicine.getStatus()))
                .orElseThrow(() -> new FieldValidationException("medicineId",
                        "Thuốc không tồn tại trong danh mục hoặc đã ngừng sử dụng"));
    }

    private Medicine find(Long id) {
        return medicineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thuốc!"));
    }

    private void auditDelete(Medicine medicine, boolean softDeleted) {
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.CATALOG_DELETE,
                AuditActions.ENTITY_MEDICINES, medicine.getId())
                .with("code", medicine.getCode())
                .with("softDeleted", softDeleted));
    }
}
