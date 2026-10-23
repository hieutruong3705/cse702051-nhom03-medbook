package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.UpdateMedicalRecordCommand;
import com.phenikaa.cse702051.medbook.dto.encounter.CreateEncounterRequest;
import com.phenikaa.cse702051.medbook.dto.encounter.EncounterDTO;
import com.phenikaa.cse702051.medbook.dto.encounter.EncounterSummaryDTO;
import com.phenikaa.cse702051.medbook.dto.encounter.UpdateEncounterRequest;
import com.phenikaa.cse702051.medbook.exception.BadRequestException;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Lần khám (YCCN-17, 18, 19). Luồng chuẩn:
 *
 * <ol>
 * <li>{@link #create}: bác sĩ phụ trách lịch hẹn BOOKED bắt đầu khám. Trong MỘT giao dịch: lịch chuyển
 * {@code BOOKED → IN_PROGRESS} và lần khám {@code OPEN} được lưu; lỗi ở bước nào cũng rollback cả hai.</li>
 * <li>{@link #update}: chỉ bác sĩ phụ trách, chỉ khi còn OPEN. {@code status = COMPLETED} chuyển lịch sang
 * COMPLETED cùng giao dịch và khóa nội dung. Tóm tắt bệnh án đi qua
 * {@code MedicalRecordService.updateClinicalSummary}, không ghi thẳng bảng {@code medical_records}.</li>
 * <li>Đọc: bệnh nhân chủ hoặc bác sĩ phụ trách; mọi lần đọc ghi audit {@code ENCOUNTER_VIEW}. Nội dung lâm sàng
 * không bao giờ được ghi vào audit (chỉ tên trường đã đổi).</li>
 * </ol>
 *
 * <p>{@code doctorId} và {@code medicalRecordId} không bao giờ nhận từ client. Nhóm phương thức
 * {@link #getById(Long)} / {@link #getAccessibleById(Long)} trả entity cho service khác (đơn thuốc, hóa đơn, tệp).
 */
@Service
public class EncounterService {

    public static final String OPEN = "OPEN";
    public static final String COMPLETED = "COMPLETED";

    private static final int MAX_PAGE_SIZE = 100;

    private final EncounterRepository encounterRepository;
    private final MedicalRecordService medicalRecordService;
    private final AppointmentService appointmentService;
    private final EncounterAccessPolicy policy;
    private final DoctorScopePolicy doctorScopePolicy;
    private final EncounterMapper mapper;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public EncounterService(
            EncounterRepository encounterRepository,
            MedicalRecordService medicalRecordService,
            AppointmentService appointmentService,
            EncounterAccessPolicy policy,
            DoctorScopePolicy doctorScopePolicy,
            EncounterMapper mapper,
            CurrentUserService currentUserService,
            AuditLogService auditLogService) {
        this.encounterRepository = encounterRepository;
        this.medicalRecordService = medicalRecordService;
        this.appointmentService = appointmentService;
        this.policy = policy;
        this.doctorScopePolicy = doctorScopePolicy;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    // ================= Tạo / cập nhật (bác sĩ phụ trách) =================

    /** Bắt đầu khám: lịch BOOKED → IN_PROGRESS và lưu lần khám OPEN trong cùng một giao dịch. */
    @Transactional
    public EncounterDTO create(CreateEncounterRequest request) {
        currentUserService.requireRole("DOCTOR");
        Long doctorId = policy.currentDoctorId();

        Appointment appointment = appointmentService.findById(request.appointmentId());
        if (!doctorId.equals(appointment.getDoctorId())) {
            throw policy.deny(AuditActions.ENTITY_APPOINTMENTS, appointment.getId(),
                    "Tạo lần khám cho lịch của bác sĩ khác");
        }
        if (encounterRepository.existsByAppointmentId(appointment.getId())) {
            throw new ConflictException("Lịch hẹn này đã có lần khám!");
        }

        // BOOKED → IN_PROGRESS (sai trạng thái ⇒ 409). Cùng giao dịch với việc lưu lần khám bên dưới.
        appointmentService.startExamination(appointment.getId());
        MedicalRecord record = medicalRecordService.getOrCreateByPatientId(appointment.getPatientId());

        LocalDateTime now = LocalDateTime.now();
        Encounter encounter = new Encounter();
        encounter.setMedicalRecordId(record.getId());
        encounter.setAppointmentId(appointment.getId());
        encounter.setDoctorId(doctorId);
        encounter.setEncounterAt(now);
        encounter.setChiefComplaint(blankToNull(request.chiefComplaint()));
        encounter.setStatus(OPEN);
        encounter.setCreatedAt(now);
        encounter.setUpdatedAt(now);
        try {
            encounter = encounterRepository.saveAndFlush(encounter);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(appointment_id): hai yêu cầu bắt đầu khám cùng lúc, chỉ một thành công
            throw new ConflictException("Lịch hẹn này đã có lần khám!");
        }

        auditLogService.record(AuditEvent.of(AuditActions.ENCOUNTER_CREATE, AuditActions.ENTITY_ENCOUNTERS,
                encounter.getId()).with("appointmentId", appointment.getId()));
        return mapper.toDTO(encounter, true);
    }

    /** Cập nhật nội dung khám; {@code status = COMPLETED} hoàn thành khám (không sửa được nữa). */
    @Transactional
    public EncounterDTO update(Long id, UpdateEncounterRequest request) {
        currentUserService.requireRole("DOCTOR");
        Encounter encounter = getById(id);
        policy.assertDoctorOwns(encounter);
        if (!OPEN.equals(encounter.getStatus())) {
            throw new ConflictException("Lần khám đã hoàn thành, không thể chỉnh sửa!");
        }

        List<String> changed = new ArrayList<>();
        if (request.chiefComplaint() != null) {
            encounter.setChiefComplaint(blankToNull(request.chiefComplaint()));
            changed.add("chiefComplaint");
        }
        if (request.diagnosis() != null) {
            encounter.setDiagnosis(blankToNull(request.diagnosis()));
            changed.add("diagnosis");
        }
        if (request.clinicalNotes() != null) {
            encounter.setClinicalNotes(blankToNull(request.clinicalNotes()));
            changed.add("clinicalNotes");
        }
        if (request.treatmentPlan() != null) {
            encounter.setTreatmentPlan(blankToNull(request.treatmentPlan()));
            changed.add("treatmentPlan");
        }
        if (request.followUpNote() != null) {
            encounter.setFollowUpNote(blankToNull(request.followUpNote()));
            changed.add("followUpNote");
        }
        if (request.clinicalSummary() != null) {
            var summary = request.clinicalSummary();
            medicalRecordService.updateClinicalSummary(encounter.getMedicalRecordId(),
                    new UpdateMedicalRecordCommand(summary.bloodType(), summary.chronicConditions(),
                            summary.allergyNotes(), summary.medicalHistory(), summary.currentMedications()));
            changed.add("clinicalSummary");
        }

        boolean completing = request.status() != null && COMPLETED.equalsIgnoreCase(request.status());
        if (completing) {
            if (encounter.getDiagnosis() == null) {
                throw new FieldValidationException("diagnosis", "Cần nhập chẩn đoán trước khi hoàn thành khám");
            }
            if (encounter.getAppointmentId() != null) {
                appointmentService.completeExamination(encounter.getAppointmentId());
            }
            encounter.setStatus(COMPLETED);
            changed.add("status");
        }

        encounter.setUpdatedAt(LocalDateTime.now());
        encounter = encounterRepository.saveAndFlush(encounter);

        auditLogService.record(AuditEvent.of(
                completing ? AuditActions.ENCOUNTER_COMPLETE : AuditActions.ENCOUNTER_UPDATE,
                AuditActions.ENTITY_ENCOUNTERS, encounter.getId()).with("changedFields", changed));
        return mapper.toDTO(encounter, OPEN.equals(encounter.getStatus()));
    }

    // ================= Đọc (luôn kiểm quyền và ghi audit) =================

    /** Chi tiết lần khám: bệnh nhân chủ hoặc bác sĩ phụ trách; ghi audit {@code ENCOUNTER_VIEW}. */
    @Transactional
    public EncounterDTO get(Long id) {
        Encounter encounter = getById(id);
        policy.assertCanRead(encounter);
        auditLogService.record(AuditEvent.of(AuditActions.ENCOUNTER_VIEW, AuditActions.ENTITY_ENCOUNTERS, id));

        CurrentUser user = currentUserService.requireCurrentUser();
        boolean editable = OPEN.equals(encounter.getStatus()) && user.hasRole("DOCTOR");
        return mapper.toDTO(encounter, editable);
    }

    /** YCCN-19: lịch sử khám của bệnh nhân đang đăng nhập, mới nhất trước. */
    @Transactional
    public PageResponse<EncounterSummaryDTO> getMyEncounters(int page, int size) {
        Long patientId = policy.currentPatientId();
        MedicalRecord record = medicalRecordService.getOrCreateByPatientId(patientId);

        Page<Encounter> result = encounterRepository.findByMedicalRecordId(record.getId(), pageable(page, size));
        auditLogService.record(AuditEvent.of(AuditActions.ENCOUNTER_VIEW, AuditActions.ENTITY_ENCOUNTERS, null)
                .with("scope", "OWN_HISTORY").with("medicalRecordId", record.getId()));
        return toPage(result);
    }

    /**
     * Tra cứu của bác sĩ theo lịch hẹn hoặc bệnh án (phải truyền ít nhất một). Chỉ trả các lần khám do chính
     * bác sĩ này thực hiện và chỉ trong phạm vi bác sĩ phụ trách.
     */
    @Transactional
    public PageResponse<EncounterSummaryDTO> search(Long appointmentId, Long medicalRecordId, int page, int size) {
        if (appointmentId == null && medicalRecordId == null) {
            throw new BadRequestException("Cần truyền appointmentId hoặc medicalRecordId!");
        }
        Long doctorId = policy.currentDoctorId();
        Pageable pageable = pageable(page, size);

        Page<Encounter> result;
        if (appointmentId != null) {
            Appointment appointment = appointmentService.findById(appointmentId);
            if (!doctorId.equals(appointment.getDoctorId())) {
                throw policy.deny(AuditActions.ENTITY_APPOINTMENTS, appointmentId,
                        "Tra cứu lần khám của lịch thuộc bác sĩ khác");
            }
            List<Encounter> found = encounterRepository.findByAppointmentId(appointmentId).stream().toList();
            result = new org.springframework.data.domain.PageImpl<>(found, pageable, found.size());
        } else {
            MedicalRecord record = medicalRecordService.findById(medicalRecordId);
            if (!doctorScopePolicy.isResponsible(doctorId, record.getPatientId())) {
                throw policy.deny(AuditActions.ENTITY_MEDICAL_RECORDS, medicalRecordId,
                        "Tra cứu lần khám của bệnh nhân ngoài phạm vi phụ trách");
            }
            result = encounterRepository.findByMedicalRecordIdAndDoctorId(medicalRecordId, doctorId, pageable);
        }

        auditLogService.record(AuditEvent.of(AuditActions.ENCOUNTER_VIEW, AuditActions.ENTITY_ENCOUNTERS, null)
                .with("scope", "DOCTOR_LOOKUP")
                .with("appointmentId", appointmentId)
                .with("medicalRecordId", medicalRecordId));
        return toPage(result);
    }

    // ================= Nội bộ cho module khác (đơn thuốc, hóa đơn, tệp) =================

    /** Lần khám theo ID, KHÔNG kiểm quyền người gọi (người gọi phải tự kiểm bằng {@link EncounterAccessPolicy}). */
    @Transactional(readOnly = true)
    public Encounter getById(Long id) {
        return encounterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần khám với ID: " + id));
    }

    /** Lần khám theo ID, đã kiểm quyền đọc (bệnh nhân chủ hoặc bác sĩ phụ trách); Admin và người khác → 403. */
    @Transactional(readOnly = true)
    public Encounter getAccessibleById(Long id) {
        Encounter encounter = getById(id);
        policy.assertCanRead(encounter);
        return encounter;
    }

    // ================= Chi tiết =================

    private PageResponse<EncounterSummaryDTO> toPage(Page<Encounter> result) {
        return new PageResponse<>(mapper.toSummaries(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private static Pageable pageable(int page, int size) {
        int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "encounterAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
