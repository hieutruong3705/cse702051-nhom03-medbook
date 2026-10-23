package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionAppointmentDTO;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionBookRequest;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.exception.UnprocessableEntityException;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.ReceptionAppointmentRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.PageRequests;
import com.phenikaa.cse702051.medbook.util.SearchTerms;

/**
 * Lịch hẹn tại quầy lễ tân: xem lịch của cả phòng khám và đặt, hủy, đổi lịch THAY bệnh nhân.
 *
 * <ul>
 * <li>Chỉ vai trò RECEPTIONIST; kiểm ở cả {@code SecurityConfig} lẫn đầu mỗi phương thức.</li>
 * <li>Người thao tác luôn lấy từ JWT. {@code patientId} trong yêu cầu đặt hộ là bệnh nhân được đặt lịch.</li>
 * <li>Chiếm và nhả khung giờ đi qua đúng phần lõi của {@link AppointmentService}, nên hai yêu cầu đồng thời (dù từ
 * quầy hay từ bệnh nhân) chỉ một bên thành công.</li>
 * <li>Không có cột "ai đặt": người làm hộ được ghi trong nhật ký, cùng giao dịch với thay đổi.</li>
 * </ul>
 */
@Service
public class ReceptionAppointmentService {

    private static final String ROLE = "RECEPTIONIST";
    private static final int MAX_REASON = 500;

    private final ReceptionAppointmentRepository receptionAppointmentRepository;
    private final AppointmentService appointmentService;
    private final PatientRepository patientRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public ReceptionAppointmentService(
            ReceptionAppointmentRepository receptionAppointmentRepository,
            AppointmentService appointmentService,
            PatientRepository patientRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService) {
        this.receptionAppointmentRepository = receptionAppointmentRepository;
        this.appointmentService = appointmentService;
        this.patientRepository = patientRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    /**
     * Lịch hẹn theo ngày khám, sớm nhất trước. Bỏ trống cả {@code from} lẫn {@code to} → chỉ hôm nay; bỏ trống một
     * đầu → đầu kia lấy cùng ngày. {@code from} sau {@code to} → 400.
     */
    @Transactional(readOnly = true)
    public PageResponse<ReceptionAppointmentDTO> list(LocalDate from, LocalDate to, Long doctorId,
            AppointmentStatus status, String keyword, int page, int size) {
        currentUserService.requireRole(ROLE);
        LocalDate start = from != null ? from : (to != null ? to : LocalDate.now());
        LocalDate end = to != null ? to : start;
        if (start.isAfter(end)) {
            throw new FieldValidationException("from", "Ngày bắt đầu không được sau ngày kết thúc");
        }
        String pattern = SearchTerms.isBlank(keyword) ? null : SearchTerms.toLikePattern(keyword);
        return PageResponse.from(receptionAppointmentRepository.search(start, end, doctorId, status, pattern,
                PageRequest.of(Math.max(page, 0), PageRequests.clampSize(size))));
    }

    @Transactional(readOnly = true)
    public ReceptionAppointmentDTO get(Long id) {
        currentUserService.requireRole(ROLE);
        return view(id);
    }

    /**
     * Đặt lịch hộ. Bệnh nhân không tồn tại → 404; không ở trạng thái ACTIVE → 422; khung giờ đã mất hoặc người khác
     * đang giữ chỗ → 409; bản giữ chỗ gửi kèm đã hết hạn → 410.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceptionAppointmentDTO book(ReceptionBookRequest request) {
        currentUserService.requireRole(ROLE);
        Patient patient = patientRepository.findForReceptionById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bệnh nhân ID: " + request.patientId()));
        if (!"ACTIVE".equalsIgnoreCase(patient.getStatus())) {
            throw new UnprocessableEntityException("Hồ sơ bệnh nhân đang ngừng hoạt động nên không đặt lịch được!");
        }

        Appointment appointment = appointmentService.bookOnBehalf(patient.getId(), request.slotId(),
                request.serviceId(), request.notes(), request.holdId());
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.APPOINTMENT_BOOK_ON_BEHALF,
                AuditActions.ENTITY_APPOINTMENTS, appointment.getId())
                .with("patientId", patient.getId())
                .with("slotId", request.slotId()));
        return view(appointment.getId());
    }

    /** Hủy lịch hộ: chỉ lịch BOOKED (khác → 422) và chưa qua giờ bắt đầu (đã qua → 409). */
    @Transactional
    public ReceptionAppointmentDTO cancel(Long appointmentId, String reason) {
        currentUserService.requireRole(ROLE);
        Appointment appointment = appointmentService.cancelOnBehalf(appointmentId, requireReason(reason));
        // lý do nằm ở cột cancel_reason của lịch hẹn; nhật ký chỉ giữ ID
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.APPOINTMENT_CANCEL_ON_BEHALF,
                AuditActions.ENTITY_APPOINTMENTS, appointment.getId())
                .with("patientId", appointment.getPatientId()));
        return view(appointment.getId());
    }

    /** Đổi lịch hộ sang khung giờ trống khác; cùng điều kiện với hủy hộ. Thất bại thì lịch cũ giữ nguyên. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceptionAppointmentDTO reschedule(Long appointmentId, Long newSlotId, String reason, Long holdId) {
        currentUserService.requireRole(ROLE);
        requireReason(reason);
        Long oldSlotId = appointmentService.findById(appointmentId).getSlot().getId();
        Appointment appointment = appointmentService.rescheduleOnBehalf(appointmentId, newSlotId, holdId);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.APPOINTMENT_RESCHEDULE_ON_BEHALF,
                AuditActions.ENTITY_APPOINTMENTS, appointment.getId())
                .with("patientId", appointment.getPatientId())
                .with("oldSlotId", oldSlotId)
                .with("newSlotId", newSlotId));
        return view(appointment.getId());
    }

    private ReceptionAppointmentDTO view(Long id) {
        return receptionAppointmentRepository.findDtoById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn!"));
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new FieldValidationException("reason", "Phải nhập lý do");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON) {
            throw new FieldValidationException("reason", "Lý do tối đa " + MAX_REASON + " ký tự");
        }
        return trimmed;
    }
}
