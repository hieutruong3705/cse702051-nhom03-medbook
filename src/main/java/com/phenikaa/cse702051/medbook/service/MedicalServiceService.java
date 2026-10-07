package com.phenikaa.cse702051.medbook.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.config.CacheConfig;
import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.catalog.MedicalServiceAdminDTO;
import com.phenikaa.cse702051.medbook.dto.catalog.MedicalServiceDTO;
import com.phenikaa.cse702051.medbook.dto.catalog.MedicalServiceRequest;
import com.phenikaa.cse702051.medbook.dto.catalog.ServicePriceSnapshot;
import com.phenikaa.cse702051.medbook.exception.FieldConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.MedicalService;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceItemRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.CatalogRules;
import com.phenikaa.cse702051.medbook.util.PageRequests;

/**
 * Danh mục dịch vụ khám (YCCN-20, 25). Khách chỉ thấy dịch vụ {@code ACTIVE}; Admin tạo, sửa, xóa. Giá luôn được
 * lưu với hai chữ số thập phân. Dịch vụ đã được chọn trong lịch hẹn hoặc đã vào dòng hóa đơn không bị xóa hẳn mà
 * chuyển sang {@code INACTIVE} để dữ liệu cũ vẫn tra được tên dịch vụ.
 */
@Service
public class MedicalServiceService {

    private static final Sort BY_NAME = Sort.by("name").ascending().and(Sort.by("id").ascending());
    private static final String DUPLICATE_CODE = "Mã dịch vụ đã được sử dụng";

    private final MedicalServiceRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final PublicCatalogCache publicCatalogCache;

    public MedicalServiceService(
            MedicalServiceRepository serviceRepository,
            AppointmentRepository appointmentRepository,
            InvoiceItemRepository invoiceItemRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            PublicCatalogCache publicCatalogCache) {
        this.serviceRepository = serviceRepository;
        this.appointmentRepository = appointmentRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.publicCatalogCache = publicCatalogCache;
    }

    // ================= Công khai =================

    /**
     * Trang đầu không kèm từ khóa (trường hợp mọi màn hình chọn danh mục đều gọi) được lấy từ bộ nhớ đệm; các
     * truy vấn khác luôn đọc CSDL.
     */
    @Cacheable(cacheNames = CacheConfig.PUBLIC_MEDICAL_SERVICES, key = "#size",
            condition = CacheConfig.FIRST_PAGE_WITHOUT_KEYWORD)
    @Transactional(readOnly = true)
    public PageResponse<MedicalServiceDTO> listActive(String keyword, int page, int size) {
        Page<MedicalService> result = serviceRepository.findAll(
                CatalogRules.matching(CatalogRules.ACTIVE, keyword, "name", "code"),
                PageRequests.of(page, size, BY_NAME));
        return PageResponse.from(result, MedicalServiceDTO::from);
    }

    /** Dịch vụ đang hoạt động theo ID; bản đã ngừng sử dụng cũng trả 404 như không tồn tại. */
    @Transactional(readOnly = true)
    public MedicalServiceDTO getActive(Long id) {
        return serviceRepository.findById(id)
                .filter(service -> CatalogRules.isActive(service.getStatus()))
                .map(MedicalServiceDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ khám!"));
    }

    // ================= Quản trị =================

    @Transactional(readOnly = true)
    public PageResponse<MedicalServiceAdminDTO> listForAdmin(String keyword, String status, int page, int size) {
        currentUserService.requireRole("ADMIN");
        Page<MedicalService> result = serviceRepository.findAll(
                CatalogRules.matching(CatalogRules.statusFilter(status), keyword, "name", "code"),
                PageRequests.of(page, size, BY_NAME));
        return PageResponse.from(result, MedicalServiceAdminDTO::from);
    }

    @Transactional(readOnly = true)
    public MedicalServiceAdminDTO getForAdmin(Long id) {
        currentUserService.requireRole("ADMIN");
        return MedicalServiceAdminDTO.from(find(id));
    }

    @Transactional
    public MedicalServiceAdminDTO create(MedicalServiceRequest request) {
        currentUserService.requireRole("ADMIN");
        publicCatalogCache.evictAfterCommit(CacheConfig.PUBLIC_MEDICAL_SERVICES);
        String code = CatalogRules.requireCode(request.code());
        if (serviceRepository.existsByCodeIgnoreCase(code)) {
            throw new FieldConflictException("code", DUPLICATE_CODE);
        }
        MedicalService service = MedicalService.builder()
                .code(code)
                .name(request.name().trim())
                .description(CatalogRules.blankToNull(request.description()))
                .durationMinutes(request.durationMinutes())
                .price(money(request.price()))
                .status(CatalogRules.statusOrDefault(request.status(), CatalogRules.ACTIVE))
                .build();
        try {
            service = serviceRepository.saveAndFlush(service);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(code): hai yêu cầu tạo cùng mã gửi đồng thời
            throw new FieldConflictException("code", DUPLICATE_CODE);
        }
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.CATALOG_CREATE,
                AuditActions.ENTITY_SERVICES, service.getId()).with("code", code));
        return MedicalServiceAdminDTO.from(service);
    }

    @Transactional
    public MedicalServiceAdminDTO update(Long id, MedicalServiceRequest request) {
        currentUserService.requireRole("ADMIN");
        publicCatalogCache.evictAfterCommit(CacheConfig.PUBLIC_MEDICAL_SERVICES);
        MedicalService service = find(id);
        CatalogRules.assertCodeUnchanged(request.code(), service.getCode());
        service.setName(request.name().trim());
        service.setDescription(CatalogRules.blankToNull(request.description()));
        service.setDurationMinutes(request.durationMinutes());
        service.setPrice(money(request.price()));
        service.setStatus(CatalogRules.statusOrDefault(request.status(), service.getStatus()));
        service = serviceRepository.saveAndFlush(service);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.CATALOG_UPDATE,
                AuditActions.ENTITY_SERVICES, id).with("code", service.getCode()));
        return MedicalServiceAdminDTO.from(service);
    }

    /**
     * Xóa dịch vụ.
     *
     * @return rỗng nếu đã xóa hẳn; bản ghi ở trạng thái {@code INACTIVE} nếu đã có lịch hẹn hoặc dòng hóa đơn dùng
     */
    @Transactional
    public Optional<MedicalServiceAdminDTO> delete(Long id) {
        currentUserService.requireRole("ADMIN");
        publicCatalogCache.evictAfterCommit(CacheConfig.PUBLIC_MEDICAL_SERVICES);
        MedicalService service = find(id);
        boolean referenced = appointmentRepository.existsForService(id) || invoiceItemRepository.existsByServiceId(id);
        if (referenced) {
            service.setStatus(CatalogRules.INACTIVE);
            service = serviceRepository.saveAndFlush(service);
            auditDelete(service, true);
            return Optional.of(MedicalServiceAdminDTO.from(service));
        }
        serviceRepository.delete(service);
        auditDelete(service, false);
        return Optional.empty();
    }

    // ================= Nội bộ cho module khác (không kiểm quyền người gọi) =================

    /**
     * Tên và đơn giá hiện tại của một dịch vụ đang hoạt động. Không tồn tại hoặc đã ngừng sử dụng → 400 ở trường
     * {@code serviceId}; không bao giờ trả {@code null}.
     */
    @Transactional(readOnly = true)
    public ServicePriceSnapshot getPriceSnapshot(Long id) {
        if (id == null) {
            throw new FieldValidationException("serviceId", "Phải chọn dịch vụ");
        }
        MedicalService service = serviceRepository.findById(id)
                .filter(found -> CatalogRules.isActive(found.getStatus()))
                .orElseThrow(() -> new FieldValidationException("serviceId",
                        "Dịch vụ không tồn tại hoặc đã ngừng sử dụng"));
        return new ServicePriceSnapshot(service.getId(), service.getName(), money(service.getPrice()));
    }

    private MedicalService find(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ khám!"));
    }

    private void auditDelete(MedicalService service, boolean softDeleted) {
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.CATALOG_DELETE,
                AuditActions.ENTITY_SERVICES, service.getId())
                .with("code", service.getCode())
                .with("softDeleted", softDeleted));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
