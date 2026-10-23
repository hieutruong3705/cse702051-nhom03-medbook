package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.prescription.PrescriptionDTO;
import com.phenikaa.cse702051.medbook.dto.prescription.PrescriptionItemDTO;
import com.phenikaa.cse702051.medbook.dto.prescription.PrescriptionRequest;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.Prescription;
import com.phenikaa.cse702051.medbook.repository.PrescriptionItemRepository;
import com.phenikaa.cse702051.medbook.repository.PrescriptionRepository;
import com.phenikaa.cse702051.medbook.util.ReferenceCodes;

/**
 * Đơn thuốc của lần khám (YCCN-18).
 *
 * <ul>
 * <li><b>Ghi</b> (kê đơn, sửa đơn, thêm/sửa/xóa dòng thuốc): chỉ bác sĩ phụ trách lần khám và chỉ khi lần khám
 * còn OPEN. Người khác → 403, lần khám đã hoàn thành → 409. Mỗi lần ghi có một bản audit.</li>
 * <li><b>Đọc</b>: bệnh nhân chủ lần khám hoặc bác sĩ phụ trách (Admin không đọc được). Mỗi yêu cầu đọc ghi đúng
 * một bản audit {@code PRESCRIPTION_VIEW} gắn với lần khám, không phải một bản cho mỗi đơn.</li>
 * <li>Đơn luôn là bản ghi mới: lần khám lấy từ đường dẫn, mã đơn ({@code RX-yyyyMMdd-XXXXXX}) do server sinh.</li>
 * </ul>
 */
@Service
public class PrescriptionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final int MAX_NOTES = 500;

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository itemRepository;
    private final EncounterService encounterService;
    private final EncounterAccessPolicy policy;
    private final AuditLogService auditLogService;

    public PrescriptionService(
            PrescriptionRepository prescriptionRepository,
            PrescriptionItemRepository itemRepository,
            EncounterService encounterService,
            EncounterAccessPolicy policy,
            AuditLogService auditLogService) {
        this.prescriptionRepository = prescriptionRepository;
        this.itemRepository = itemRepository;
        this.encounterService = encounterService;
        this.policy = policy;
        this.auditLogService = auditLogService;
    }

    // ================= Ghi (bác sĩ phụ trách, lần khám còn OPEN) =================

    @Transactional
    public PrescriptionDTO create(Long encounterId, PrescriptionRequest request) {
        Encounter encounter = encounterService.getById(encounterId);
        policy.assertDoctorOwns(encounter);
        assertEncounterOpen(encounter);

        LocalDateTime now = LocalDateTime.now();
        Prescription prescription = new Prescription();
        prescription.setEncounterId(encounterId);
        prescription.setPrescriptionCode(newCode(now.toLocalDate()));
        prescription.setIssuedAt(now);
        prescription.setNotes(notesOf(request == null ? null : request.notes()));
        prescription.setStatus(STATUS_ACTIVE);
        prescription.setCreatedAt(now);
        prescription.setUpdatedAt(now);
        try {
            prescription = prescriptionRepository.saveAndFlush(prescription);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(prescription_code): trùng mã với một đơn vừa được tạo ở yêu cầu khác
            throw new ConflictException("Không tạo được mã đơn thuốc. Vui lòng thử lại!");
        }
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.PRESCRIPTION_CREATE,
                AuditActions.ENTITY_PRESCRIPTIONS, prescription.getId()).with("encounterId", encounterId));
        return PrescriptionDTO.from(prescription, List.of());
    }

    /** Sửa ghi chú hoặc trạng thái của đơn; không đổi được lần khám hay mã đơn. */
    @Transactional
    public PrescriptionDTO update(Long id, PrescriptionRequest request) {
        Prescription prescription = getForEdit(id);
        if (request != null && request.notes() != null) {
            prescription.setNotes(notesOf(request.notes()));
        }
        if (request != null && request.status() != null && !request.status().isBlank()) {
            prescription.setStatus(statusOf(request.status()));
        }
        prescription.setUpdatedAt(LocalDateTime.now());
        prescription = prescriptionRepository.saveAndFlush(prescription);
        auditWrite(prescription, "PRESCRIPTION_EDITED", null);
        return PrescriptionDTO.from(prescription, itemsOf(prescription.getId()));
    }

    // ================= Đọc (bệnh nhân chủ hoặc bác sĩ phụ trách) =================

    @Transactional(readOnly = true)
    public List<PrescriptionDTO> listByEncounter(Long encounterId) {
        Encounter encounter = encounterService.getById(encounterId);
        policy.assertCanRead(encounter);
        List<Prescription> prescriptions = prescriptionRepository.findByEncounterIdOrderByIdAsc(encounterId);
        Map<Long, List<PrescriptionItemDTO>> items = prescriptions.isEmpty() ? Map.of()
                : itemRepository.findByPrescriptionIdInOrderByIdAsc(
                        prescriptions.stream().map(Prescription::getId).toList()).stream()
                        .collect(Collectors.groupingBy(item -> item.getPrescriptionId(),
                                Collectors.mapping(PrescriptionItemDTO::from, Collectors.toList())));
        auditView(encounterId, prescriptions.size());
        return prescriptions.stream()
                .map(prescription -> PrescriptionDTO.from(prescription,
                        items.getOrDefault(prescription.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PrescriptionDTO get(Long id) {
        Prescription prescription = getReadable(id);
        auditView(prescription.getEncounterId(), 1);
        return PrescriptionDTO.from(prescription, itemsOf(id));
    }

    // ================= Dùng chung với PrescriptionItemService =================

    /**
     * Đơn thuốc mà người gọi được phép THAY ĐỔI (hoặc thêm/sửa/xóa dòng thuốc của nó): phải là bác sĩ phụ trách
     * lần khám (bệnh nhân/bác sĩ khác/Admin → 403, không có đơn → 404) và lần khám còn OPEN (đã hoàn thành → 409).
     */
    @Transactional(readOnly = true)
    public Prescription getForEdit(Long prescriptionId) {
        Prescription prescription = find(prescriptionId);
        Encounter encounter = encounterService.getById(prescription.getEncounterId());
        policy.assertDoctorOwns(encounter);
        assertEncounterOpen(encounter);
        return prescription;
    }

    /** Đơn thuốc mà người gọi được phép ĐỌC (không ghi audit; người gọi tự ghi một bản cho cả yêu cầu). */
    @Transactional(readOnly = true)
    public Prescription getReadable(Long prescriptionId) {
        Prescription prescription = find(prescriptionId);
        policy.assertCanRead(encounterService.getById(prescription.getEncounterId()));
        return prescription;
    }

    /** Một bản audit cho một yêu cầu đọc đơn thuốc của lần khám; metadata chỉ có số đơn. */
    public void auditView(Long encounterId, int prescriptionCount) {
        auditLogService.record(AuditEvent.of(AuditActions.PRESCRIPTION_VIEW, AuditActions.ENTITY_ENCOUNTERS,
                encounterId).with("prescriptionCount", prescriptionCount));
    }

    /** Một bản audit cho một lần thay đổi đơn hoặc dòng thuốc; metadata chỉ có mã định danh và loại thay đổi. */
    public void auditWrite(Prescription prescription, String change, Long itemId) {
        AuditEvent event = AuditEvent.of(AuditActions.PRESCRIPTION_UPDATE, AuditActions.ENTITY_PRESCRIPTIONS,
                prescription.getId())
                .with("encounterId", prescription.getEncounterId())
                .with("change", change);
        auditLogService.recordInCurrentTransaction(itemId == null ? event : event.with("itemId", itemId));
    }

    // ================= Chi tiết =================

    private Prescription find(Long id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuốc!"));
    }

    private List<PrescriptionItemDTO> itemsOf(Long prescriptionId) {
        return itemRepository.findByPrescriptionIdOrderByIdAsc(prescriptionId).stream()
                .map(PrescriptionItemDTO::from)
                .toList();
    }

    private void assertEncounterOpen(Encounter encounter) {
        if (!EncounterService.OPEN.equalsIgnoreCase(encounter.getStatus())) {
            throw new ConflictException("Lần khám đã hoàn thành nên không thể kê hoặc sửa đơn thuốc");
        }
    }

    /** Mã {@code RX-yyyyMMdd-XXXXXX}; xem {@link ReferenceCodes#next}. */
    private String newCode(LocalDate date) {
        return ReferenceCodes.next("RX", date, prescriptionRepository::existsByPrescriptionCode);
    }

    private static String notesOf(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.length() > MAX_NOTES) {
            throw new FieldValidationException("notes", "Ghi chú đơn thuốc tối đa " + MAX_NOTES + " ký tự");
        }
        return trimmed;
    }

    private static String statusOf(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_CANCELLED.equals(normalized)) {
            throw new FieldValidationException("status", "Trạng thái đơn thuốc phải là ACTIVE hoặc CANCELLED");
        }
        return normalized;
    }
}
