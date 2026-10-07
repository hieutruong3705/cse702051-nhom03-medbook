package com.phenikaa.cse702051.medbook.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.invoice.AdminInvoiceDTO;
import com.phenikaa.cse702051.medbook.dto.invoice.InvoiceDTO;
import com.phenikaa.cse702051.medbook.dto.invoice.InvoiceItemDTO;
import com.phenikaa.cse702051.medbook.dto.invoice.InvoiceLineRequest;
import com.phenikaa.cse702051.medbook.dto.invoice.InvoiceSummaryDTO;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.model.InvoiceItem;
import com.phenikaa.cse702051.medbook.model.MedicalService;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceItemRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.CatalogRules;
import com.phenikaa.cse702051.medbook.util.DateRanges;
import com.phenikaa.cse702051.medbook.util.PageRequests;
import com.phenikaa.cse702051.medbook.util.ReferenceCodes;
import com.phenikaa.cse702051.medbook.util.SearchTerms;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Hóa đơn của lần khám (YCCN-22). Không có cổng thanh toán: "đã thu" là việc Admin ghi nhận tiền thu tại quầy.
 *
 * <ul>
 * <li><b>Lập</b>: chỉ bác sĩ phụ trách lần khám. Mọi dòng phải là một dịch vụ đang hoạt động trong danh mục; tên
 * và đơn giá luôn lấy từ danh mục, không bao giờ từ client. Mỗi lần khám (lịch hẹn) có nhiều nhất một hóa đơn,
 * được bảo đảm bằng UNIQUE({@code appointment_id}) nên hai yêu cầu lập cùng lúc chỉ một yêu cầu thành công.</li>
 * <li><b>Đọc</b>: bệnh nhân chủ hóa đơn, bác sĩ phụ trách lần khám và Admin (hóa đơn là dữ liệu hành chính, các
 * dòng là dịch vụ chứ không phải nội dung khám). Người khác → 403 kèm audit.</li>
 * <li><b>Thu, hủy</b>: chỉ Admin, chỉ từ {@code UNPAID}, bằng cập nhật có điều kiện ở CSDL nên thu và hủy gửi
 * cùng lúc chỉ một yêu cầu thắng, yêu cầu kia nhận 409.</li>
 * <li>Tiền dùng {@code BigDecimal} hai chữ số thập phân, làm tròn {@code HALF_UP}.</li>
 * </ul>
 */
@Service
public class InvoiceService {

    public static final String UNPAID = "UNPAID";
    public static final String PAID = "PAID";
    public static final String VOID = "VOID";

    static final int MAX_LINES = 20;
    static final int MAX_QUANTITY = 100;
    private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999.99");
    private static final int MAX_VOID_REASON = 255;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "issuedAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));
    private static final String ALREADY_INVOICED = "Lần khám này đã có hóa đơn";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository itemRepository;
    private final EncounterRepository encounterRepository;
    private final EncounterService encounterService;
    private final MedicalRecordService medicalRecordService;
    private final MedicalServiceRepository serviceRepository;
    private final PatientRepository patientRepository;
    private final EncounterAccessPolicy policy;
    private final CurrentUserService currentUserService;
    private final CurrentActorService currentActorService;
    private final AuditLogService auditLogService;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceItemRepository itemRepository,
            EncounterRepository encounterRepository,
            EncounterService encounterService,
            MedicalRecordService medicalRecordService,
            MedicalServiceRepository serviceRepository,
            PatientRepository patientRepository,
            EncounterAccessPolicy policy,
            CurrentUserService currentUserService,
            CurrentActorService currentActorService,
            AuditLogService auditLogService) {
        this.invoiceRepository = invoiceRepository;
        this.itemRepository = itemRepository;
        this.encounterRepository = encounterRepository;
        this.encounterService = encounterService;
        this.medicalRecordService = medicalRecordService;
        this.serviceRepository = serviceRepository;
        this.patientRepository = patientRepository;
        this.policy = policy;
        this.currentUserService = currentUserService;
        this.currentActorService = currentActorService;
        this.auditLogService = auditLogService;
    }

    // ================= Lập hóa đơn (bác sĩ phụ trách) =================

    /**
     * Lập hóa đơn cho lần khám (đang mở hoặc đã hoàn thành đều được).
     *
     * @param lines          các dòng {@code {serviceId, quantity}}: 1–20 dòng, không lặp dịch vụ, số lượng nguyên 1–100
     * @param discountAmount giảm giá, {@code 0 ≤ giảm giá ≤ tạm tính}
     */
    @Transactional
    public InvoiceDTO create(Long encounterId, List<InvoiceLineRequest> lines, BigDecimal discountAmount) {
        Encounter encounter = encounterService.getById(encounterId);
        policy.assertDoctorOwns(encounter);

        BigDecimal discount = discountOf(discountAmount);
        Map<Long, Integer> quantities = quantitiesOf(lines);
        Map<Long, MedicalService> services = activeServices(quantities.keySet());

        Long appointmentId = encounter.getAppointmentId();
        if (appointmentId == null) {
            throw new ConflictException("Lần khám này không gắn với lịch hẹn nên không lập được hóa đơn!");
        }
        if (invoiceRepository.existsByAppointmentId(appointmentId)) {
            throw new ConflictException(ALREADY_INVOICED);
        }

        List<InvoiceItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            MedicalService service = services.get(line.getKey());
            BigDecimal unitPrice = money(service.getPrice());
            BigDecimal lineTotal = money(unitPrice.multiply(BigDecimal.valueOf(line.getValue())));
            InvoiceItem item = new InvoiceItem();
            item.setServiceId(service.getId());
            item.setDescription(service.getName());
            item.setQuantity(BigDecimal.valueOf(line.getValue()));
            item.setUnitPrice(unitPrice);
            item.setLineTotal(lineTotal);
            items.add(item);
            subtotal = subtotal.add(lineTotal);
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new FieldValidationException("discountAmount", "Giảm giá không được lớn hơn tạm tính");
        }
        BigDecimal total = subtotal.subtract(discount);
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw new FieldValidationException("items", "Tổng giá trị hóa đơn vượt quá giới hạn cho phép");
        }

        LocalDateTime now = LocalDateTime.now();
        Invoice invoice = new Invoice();
        invoice.setInvoiceCode(ReferenceCodes.next("INV", now.toLocalDate(), invoiceRepository::existsByInvoiceCode));
        invoice.setPatientId(medicalRecordService.findById(encounter.getMedicalRecordId()).getPatientId());
        invoice.setAppointmentId(appointmentId);
        invoice.setSubtotal(subtotal);
        invoice.setDiscountAmount(discount);
        invoice.setTotalAmount(total);
        invoice.setStatus(UNPAID);
        invoice.setIssuedAt(now);
        try {
            invoice = invoiceRepository.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(appointment_id): một yêu cầu khác vừa lập hóa đơn cho cùng lần khám
            throw new ConflictException(ALREADY_INVOICED);
        }
        for (InvoiceItem item : items) {
            item.setInvoiceId(invoice.getId());
        }
        List<InvoiceItem> saved = itemRepository.saveAll(items);

        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.INVOICE_CREATE,
                AuditActions.ENTITY_INVOICES, invoice.getId())
                .with("encounterId", encounterId)
                .with("totalAmount", total.toPlainString()));
        return InvoiceDTO.from(invoice, patientNameOf(invoice.getPatientId()), encounterId,
                saved.stream().map(InvoiceItemDTO::from).toList());
    }

    // ================= Đọc =================

    /** Hóa đơn của một lần khám: bệnh nhân chủ hoặc bác sĩ phụ trách (Admin → 403); chưa lập → 404. */
    @Transactional(readOnly = true)
    public InvoiceDTO getByEncounter(Long encounterId) {
        Encounter encounter = encounterService.getById(encounterId);
        policy.assertCanRead(encounter);
        Invoice invoice = (encounter.getAppointmentId() == null
                ? Optional.<Invoice>empty()
                : invoiceRepository.findByAppointmentId(encounter.getAppointmentId()))
                .orElseThrow(() -> new ResourceNotFoundException("Lần khám này chưa có hóa đơn"));
        return toDTO(invoice, encounterId);
    }

    /** Chi tiết hóa đơn: bệnh nhân chủ, bác sĩ phụ trách lần khám hoặc Admin. */
    @Transactional(readOnly = true)
    public InvoiceDTO get(Long invoiceId) {
        Invoice invoice = readable(invoiceId);
        return toDTO(invoice, encounterIdOf(invoice));
    }

    @Transactional(readOnly = true)
    public List<InvoiceItemDTO> listItems(Long invoiceId) {
        readable(invoiceId);
        return itemsOf(invoiceId);
    }

    /** Hóa đơn của bệnh nhân đang đăng nhập, mới nhất trước; lọc theo trạng thái và ngày lập. */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummaryDTO> listMine(String status, LocalDate from, LocalDate to, int page,
            int size) {
        currentUserService.requireRole("PATIENT");
        Long patientId = currentActorService.requireCurrentPatientId();
        DateRanges.requireOrdered(from, to);
        Specification<Invoice> filter = filter(statusOf(status), from, to, null)
                .and((root, query, cb) -> cb.equal(root.get("patientId"), patientId));
        Page<Invoice> result = invoiceRepository.findAll(filter, PageRequests.of(page, size, NEWEST_FIRST));
        return PageResponse.from(result, InvoiceSummaryDTO::from);
    }

    /** Mọi hóa đơn cho Admin; {@code keyword} khớp mã hóa đơn, tên hoặc mã bệnh nhân. */
    @Transactional(readOnly = true)
    public PageResponse<AdminInvoiceDTO> listForAdmin(String status, LocalDate from, LocalDate to, String keyword,
            int page, int size) {
        currentUserService.requireRole("ADMIN");
        DateRanges.requireOrdered(from, to);
        Page<Invoice> result = invoiceRepository.findAll(filter(statusOf(status), from, to, keyword),
                PageRequests.of(page, size, NEWEST_FIRST));
        // Tra bệnh nhân theo lô: một truy vấn cho cả trang
        Map<Long, Patient> patients = new HashMap<>();
        patientRepository.findAllById(result.getContent().stream().map(Invoice::getPatientId).toList())
                .forEach(patient -> patients.put(patient.getId(), patient));
        return PageResponse.from(result, invoice -> AdminInvoiceDTO.from(invoice,
                patients.get(invoice.getPatientId())));
    }

    // ================= Thu và hủy (Admin) =================

    /** Ghi nhận đã thu. Chỉ từ {@code UNPAID}; đã thu, đã hủy hoặc thua một yêu cầu đồng thời → 409. */
    @Transactional
    public AdminInvoiceDTO collect(Long invoiceId) {
        currentUserService.requireRole("ADMIN");
        if (invoiceRepository.markPaid(invoiceId, LocalDateTime.now()) == 0) {
            throw notChangeable(invoiceId, "thu");
        }
        return afterTransition(invoiceId, AuditActions.INVOICE_COLLECT);
    }

    /** Hủy hóa đơn chưa thu, bắt buộc có lý do. Đã thu, đã hủy hoặc thua một yêu cầu đồng thời → 409. */
    @Transactional
    public AdminInvoiceDTO voidInvoice(Long invoiceId, String reason) {
        currentUserService.requireRole("ADMIN");
        if (reason == null || reason.isBlank()) {
            throw new FieldValidationException("reason", "Phải nhập lý do hủy hóa đơn");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_VOID_REASON) {
            throw new FieldValidationException("reason", "Lý do hủy tối đa " + MAX_VOID_REASON + " ký tự");
        }
        if (invoiceRepository.markVoid(invoiceId, trimmed, LocalDateTime.now()) == 0) {
            throw notChangeable(invoiceId, "hủy");
        }
        return afterTransition(invoiceId, AuditActions.INVOICE_VOID);
    }

    // ================= Chi tiết =================

    private AdminInvoiceDTO afterTransition(Long invoiceId, String action) {
        Invoice invoice = find(invoiceId);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(action, AuditActions.ENTITY_INVOICES, invoiceId)
                .with("totalAmount", invoice.getTotalAmount().toPlainString()));
        return AdminInvoiceDTO.from(invoice, patientRepository.findById(invoice.getPatientId()).orElse(null));
    }

    /** Không cập nhật được dòng nào: hóa đơn không tồn tại (404) hoặc không còn ở trạng thái chưa thu (409). */
    private RuntimeException notChangeable(Long invoiceId, String action) {
        Invoice invoice = find(invoiceId);
        String state = PAID.equals(invoice.getStatus()) ? "đã thu" : VOID.equals(invoice.getStatus()) ? "đã hủy"
                : "vừa được người khác xử lý";
        return new ConflictException("Không thể " + action + " hóa đơn " + invoice.getInvoiceCode() + " vì hóa đơn "
                + state + ". Chỉ hóa đơn chưa thu mới " + action + " được!");
    }

    private Invoice find(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn!"));
    }

    /** Hóa đơn mà người gọi được đọc; người không liên quan → 403 kèm audit {@code ACCESS_DENIED}. */
    private Invoice readable(Long invoiceId) {
        Invoice invoice = find(invoiceId);
        CurrentUser user = currentUserService.requireCurrentUser();
        if (user.hasRole("ADMIN")) {
            return invoice;
        }
        boolean owner = user.hasRole("PATIENT") && currentActorService.findCurrentPatientId()
                .map(patientId -> patientId.equals(invoice.getPatientId())).orElse(false);
        if (owner) {
            return invoice;
        }
        boolean attendingDoctor = user.hasRole("DOCTOR") && invoice.getAppointmentId() != null
                && currentActorService.findCurrentDoctorId()
                        .flatMap(doctorId -> encounterRepository.findByAppointmentId(invoice.getAppointmentId())
                                .map(encounter -> doctorId.equals(encounter.getDoctorId())))
                        .orElse(false);
        if (attendingDoctor) {
            return invoice;
        }
        throw policy.deny(AuditActions.ENTITY_INVOICES, invoiceId, "Không phải bệnh nhân chủ hóa đơn hay bác sĩ phụ trách");
    }

    private InvoiceDTO toDTO(Invoice invoice, Long encounterId) {
        return InvoiceDTO.from(invoice, patientNameOf(invoice.getPatientId()), encounterId,
                itemsOf(invoice.getId()));
    }

    private List<InvoiceItemDTO> itemsOf(Long invoiceId) {
        return itemRepository.findByInvoiceIdOrderByIdAsc(invoiceId).stream().map(InvoiceItemDTO::from).toList();
    }

    private Long encounterIdOf(Invoice invoice) {
        if (invoice.getAppointmentId() == null) {
            return null;
        }
        return encounterRepository.findByAppointmentId(invoice.getAppointmentId()).map(Encounter::getId).orElse(null);
    }

    private String patientNameOf(Long patientId) {
        return patientRepository.findById(patientId).map(Patient::getFullName).orElse(null);
    }

    /** Dịch vụ của các dòng, tra theo lô. Không tồn tại → 404; đã ngừng sử dụng → 400 ở trường {@code serviceId}. */
    private Map<Long, MedicalService> activeServices(Set<Long> serviceIds) {
        Map<Long, MedicalService> services = new HashMap<>();
        serviceRepository.findAllById(serviceIds).forEach(service -> services.put(service.getId(), service));
        for (Long serviceId : serviceIds) {
            MedicalService service = services.get(serviceId);
            if (service == null) {
                throw new ResourceNotFoundException("Không tìm thấy dịch vụ với ID: " + serviceId);
            }
            if (!CatalogRules.isActive(service.getStatus())) {
                throw new FieldValidationException("serviceId",
                        "Dịch vụ \"" + service.getName() + "\" đã ngừng sử dụng");
            }
        }
        return services;
    }

    /** Kiểm tra các dòng và trả {dịch vụ → số lượng} theo đúng thứ tự client gửi. */
    private static Map<Long, Integer> quantitiesOf(List<InvoiceLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new FieldValidationException("items", "Hóa đơn phải có ít nhất một dịch vụ");
        }
        if (lines.size() > MAX_LINES) {
            throw new FieldValidationException("items", "Hóa đơn tối đa " + MAX_LINES + " dòng");
        }
        Map<Long, Integer> quantities = new java.util.LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        for (InvoiceLineRequest line : lines) {
            if (line == null || line.serviceId() == null) {
                throw new FieldValidationException("serviceId", "Mỗi dòng hóa đơn phải chọn một dịch vụ trong danh mục");
            }
            if (!seen.add(line.serviceId())) {
                throw new FieldValidationException("serviceId", "Một dịch vụ chỉ được xuất hiện một lần trong hóa đơn");
            }
            quantities.put(line.serviceId(), quantityOf(line.quantity()));
        }
        return quantities;
    }

    private static int quantityOf(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new FieldValidationException("quantity", "Số lượng phải lớn hơn 0");
        }
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw new FieldValidationException("quantity", "Số lượng phải là số nguyên");
        }
        if (quantity.compareTo(BigDecimal.valueOf(MAX_QUANTITY)) > 0) {
            throw new FieldValidationException("quantity", "Số lượng tối đa " + MAX_QUANTITY);
        }
        return quantity.intValueExact();
    }

    private static BigDecimal discountOf(BigDecimal discountAmount) {
        if (discountAmount == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (discountAmount.signum() < 0) {
            throw new FieldValidationException("discountAmount", "Giảm giá không được âm");
        }
        if (discountAmount.stripTrailingZeros().scale() > 2) {
            throw new FieldValidationException("discountAmount", "Giảm giá chỉ có tối đa hai chữ số thập phân");
        }
        return money(discountAmount);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String statusOf(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!UNPAID.equals(normalized) && !PAID.equals(normalized) && !VOID.equals(normalized)) {
            throw new FieldValidationException("status", "Trạng thái hóa đơn phải là UNPAID, PAID hoặc VOID");
        }
        return normalized;
    }

    /** Lọc theo trạng thái, ngày lập (tính trọn hai đầu) và từ khóa (mã hóa đơn, tên hoặc mã bệnh nhân). */
    private static Specification<Invoice> filter(String status, LocalDate from, LocalDate to, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (status != null) {
                all.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("issuedAt"), from.atStartOfDay()));
            }
            if (to != null) {
                all.add(cb.lessThan(root.get("issuedAt"), to.plusDays(1).atStartOfDay()));
            }
            if (!SearchTerms.isBlank(keyword)) {
                String pattern = SearchTerms.toLikePattern(keyword);
                Subquery<Long> matchingPatients = query.subquery(Long.class);
                Root<Patient> patient = matchingPatients.from(Patient.class);
                matchingPatients.select(patient.get("id")).where(cb.or(
                        cb.like(cb.lower(patient.get("fullName")), pattern, SearchTerms.ESCAPE),
                        cb.like(cb.lower(patient.get("patientCode")), pattern, SearchTerms.ESCAPE)));
                all.add(cb.or(
                        cb.like(cb.lower(root.get("invoiceCode")), pattern, SearchTerms.ESCAPE),
                        root.get("patientId").in(matchingPatients)));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }
}
