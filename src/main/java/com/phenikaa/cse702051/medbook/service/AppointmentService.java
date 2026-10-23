package com.phenikaa.cse702051.medbook.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.AppointmentDTO;
import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.event.AppointmentBookedEvent;
import com.phenikaa.cse702051.medbook.event.AppointmentCancelledEvent;
import com.phenikaa.cse702051.medbook.event.AppointmentRescheduledEvent;
import com.phenikaa.cse702051.medbook.exception.UnprocessableEntityException;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.MedicalService;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Đặt, hủy, đổi lịch khám và chuyển trạng thái (YCCN-09…16).
 *
 * <h3>Chống đặt trùng (xem docs/booking-concurrency.md)</h3>
 * <ol>
 * <li>Bệnh nhân lấy từ JWT, không nhận từ request.</li>
 * <li>Đặt lịch và đổi lịch khóa hàng của khung giờ trước ({@code findByIdForUpdate}), rồi mới kiểm bản giữ chỗ
 * ({@link SlotHoldService#consumeForBooking}) và trạng thái khung giờ trong chính giao dịch đó: người khác đang giữ
 * còn hạn → 409, bản giữ gửi kèm đã hết hạn → 410.</li>
 * <li>Mỗi slot có cột {@code version} ({@code @Version}). Đặt lịch đổi slot sang BOOKED rồi
 * {@code saveAndFlush}: thay đổi không đi qua khóa hàng (ví dụ bác sĩ gỡ khung giờ) vẫn bị phát hiện qua
 * version và trả 409.</li>
 * <li>Lớp bảo vệ thứ hai: {@code appointments.active_slot_id} UNIQUE (NULL khi đã hủy) nên CSDL
 * từ chối lịch thứ hai trên cùng slot đang hiệu lực, còn slot đã hủy đặt lại được.</li>
 * <li>Đổi lịch chiếm slot MỚI trước (có kiểm soát xung đột), sau đó mới nhả slot cũ, tất cả trong
 * một giao dịch: lỗi ở bước nào cũng rollback, lịch cũ giữ nguyên.</li>
 * <li>Thông báo chỉ phát qua sự kiện {@code AFTER_COMMIT}.</li>
 * </ol>
 *
 * <h3>Trạng thái</h3>
 * {@code BOOKED → IN_PROGRESS → COMPLETED}, nhánh {@code CANCELLED} (chỉ từ BOOKED). Chuyển
 * sai thứ tự → 409.
 */
@Service
public class AppointmentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final MedicalServiceRepository serviceRepository;
    private final CurrentUserService currentUserService;
    private final CurrentActorService currentActorService;
    private final AppointmentMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SlotHoldService slotHoldService;
    private final long cancelBeforeHours;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentSlotRepository slotRepository,
            MedicalServiceRepository serviceRepository,
            CurrentUserService currentUserService,
            CurrentActorService currentActorService,
            AppointmentMapper mapper,
            ApplicationEventPublisher eventPublisher,
            SlotHoldService slotHoldService,
            @Value("${medbook.booking.cancel-before-hours:2}") long cancelBeforeHours) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.serviceRepository = serviceRepository;
        this.currentUserService = currentUserService;
        this.currentActorService = currentActorService;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.slotHoldService = slotHoldService;
        this.cancelBeforeHours = cancelBeforeHours;
    }

    // ================= Đặt / hủy / đổi lịch (bệnh nhân) =================

    /**
     * YCCN-10, 11: đặt lịch. Chính xác một trong N yêu cầu đồng thời thành công, còn lại 409. {@code holdId} là bản
     * giữ chỗ nhận được khi chọn khung giờ (tùy chọn); đã hết hạn → 410.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentDTO book(Long slotId, Long serviceId, String notes, Long holdId) {
        currentUserService.requireRole("PATIENT");
        Long patientId = currentActorService.requireCurrentPatientId();
        return mapper.toDTO(bookFor(patientId, slotId, serviceId, notes, holdId));
    }

    /** YCCN-12: bệnh nhân hủy lịch của mình, giải phóng slot. */
    @Transactional
    public AppointmentDTO cancel(Long appointmentId, String reason) {
        currentUserService.requireRole("PATIENT");
        Long patientId = currentActorService.requireCurrentPatientId();
        Appointment appointment = requireOwnedByPatient(appointmentId, patientId);

        assertChangeableByPatient(appointment, "hủy");
        return mapper.toDTO(cancelBooked(appointment, reason));
    }

    /**
     * YCCN-13: đổi lịch sang slot trống khác, nguyên tử: chiếm slot mới trước, rồi nhả slot cũ.
     * Thất bại ở bất kỳ bước nào ⇒ rollback, lịch cũ và slot cũ giữ nguyên.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentDTO reschedule(Long appointmentId, Long newSlotId, String reason, Long holdId) {
        currentUserService.requireRole("PATIENT");
        Long patientId = currentActorService.requireCurrentPatientId();
        Appointment appointment = requireOwnedByPatient(appointmentId, patientId);

        assertChangeableByPatient(appointment, "đổi");
        return mapper.toDTO(moveToSlot(appointment, newSlotId, holdId,
                "Khung giờ mới vừa có người khác đặt. Lịch hiện tại của bạn được giữ nguyên!"));
    }

    // ================= Đặt / hủy / đổi lịch hộ tại quầy (nội bộ cho module lễ tân) =================
    // Ba phương thức dưới đây KHÔNG kiểm quyền người gọi và không ghi nhật ký: người gọi (ReceptionAppointmentService)
    // đã kiểm vai trò lễ tân và tự ghi nhật ký "làm hộ". Chúng dùng đúng phần lõi của luồng bệnh nhân, nên bảo đảm
    // đồng thời (khóa hàng của slot, bản giữ chỗ, @Version và UNIQUE active_slot_id) là một. Bản giữ chỗ gửi kèm
    // phải là của chính người đang thao tác, tức lễ tân.

    /** Đặt lịch cho bệnh nhân {@code patientId}; người gọi đã kiểm bệnh nhân tồn tại và đang hoạt động. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Appointment bookOnBehalf(Long patientId, Long slotId, Long serviceId, String notes, Long holdId) {
        return bookFor(patientId, slotId, serviceId, notes, holdId);
    }

    /**
     * Hủy hộ một lịch BOOKED. Khác bệnh nhân tự hủy: không bị chặn trong {@code cancel-before-hours} giờ trước giờ
     * khám, nhưng không hủy được lịch đã qua giờ bắt đầu.
     */
    @Transactional
    public Appointment cancelOnBehalf(Long appointmentId, String reason) {
        Appointment appointment = findById(appointmentId);
        assertChangeableAtDesk(appointment, "hủy");
        return cancelBooked(appointment, reason);
    }

    /** Đổi hộ một lịch BOOKED sang slot trống khác; cùng điều kiện với {@link #cancelOnBehalf}. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Appointment rescheduleOnBehalf(Long appointmentId, Long newSlotId, Long holdId) {
        Appointment appointment = findById(appointmentId);
        assertChangeableAtDesk(appointment, "đổi");
        return moveToSlot(appointment, newSlotId, holdId,
                "Khung giờ mới vừa có người khác đặt. Lịch hiện tại được giữ nguyên!");
    }

    // ================= Phần lõi dùng chung của đặt / hủy / đổi =================

    private Appointment bookFor(Long patientId, Long slotId, Long serviceId, String notes, Long holdId) {
        MedicalService service = requireActiveService(serviceId);
        // khóa hàng của khung giờ trước, rồi mới kiểm bản giữ chỗ và trạng thái
        AppointmentSlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung giờ khám!"));

        slotHoldService.consumeForBooking(slotId, holdId);
        assertSlotBookable(slot, "Khung giờ này");
        assertServiceFits(service, slot);
        takeSlot(slot, "Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác!");

        Appointment appointment = Appointment.builder()
                .patientId(patientId)
                .serviceId(service.getId())
                .status(AppointmentStatus.BOOKED)
                .notes(notes == null || notes.isBlank() ? null : notes.trim())
                .build();
        appointment.assignSlot(slot);
        try {
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(active_slot_id): lớp bảo vệ thứ hai khi có lịch khác đã giữ slot này
            throw new ConflictException("Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác!");
        }

        eventPublisher.publishEvent(new AppointmentBookedEvent(appointment.getId(), patientId,
                slot.getDoctor().getId(), slot.getSlotDate(), slot.getStartTime(), slot.getEndTime()));
        return appointment;
    }

    private Appointment cancelBooked(Appointment appointment, String reason) {
        AppointmentSlot slot = appointment.getSlot();
        LocalDateTime now = LocalDateTime.now();
        appointment.cancel(reason == null || reason.isBlank() ? null : reason.trim(), now);
        releaseSlot(slot);
        appointmentRepository.save(appointment);
        flushOrConflict("Lịch hẹn vừa có thay đổi. Vui lòng tải lại và thử lại!");

        eventPublisher.publishEvent(new AppointmentCancelledEvent(appointment.getId(), appointment.getPatientId(),
                slot.getDoctor().getId(), slot.getSlotDate(), slot.getStartTime(), appointment.getCancelReason()));
        return appointment;
    }

    private Appointment moveToSlot(Appointment appointment, Long newSlotId, Long holdId, String slotTakenMessage) {
        AppointmentSlot oldSlot = appointment.getSlot();
        if (oldSlot.getId().equals(newSlotId)) {
            throw new FieldValidationException("newSlotId", "Khung giờ mới phải khác khung giờ hiện tại");
        }
        // khóa hàng của khung giờ mới trước, rồi mới kiểm bản giữ chỗ và trạng thái
        AppointmentSlot newSlot = slotRepository.findByIdForUpdate(newSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("Khung giờ mới không tồn tại!"));
        slotHoldService.consumeForBooking(newSlotId, holdId);
        assertSlotBookable(newSlot, "Khung giờ mới");
        if (appointment.getServiceId() != null) {
            serviceRepository.findById(appointment.getServiceId())
                    .ifPresent(service -> assertServiceFits(service, newSlot));
        }

        LocalDate oldDate = oldSlot.getSlotDate();
        var oldStart = oldSlot.getStartTime();
        Long oldDoctorId = oldSlot.getDoctor().getId();

        // 1. chiếm slot mới (xung đột ⇒ 409, toàn bộ giao dịch rollback)
        takeSlot(newSlot, slotTakenMessage);
        // 2. nhả slot cũ
        releaseSlot(oldSlot);
        // 3. chuyển lịch hẹn sang slot mới
        appointment.assignSlot(newSlot);
        appointment.setReminder24hSentAt(null);
        appointment.setReminder2hSentAt(null);
        appointmentRepository.save(appointment);
        flushOrConflict(slotTakenMessage);

        eventPublisher.publishEvent(new AppointmentRescheduledEvent(appointment.getId(), appointment.getPatientId(),
                oldDoctorId, oldDate, oldStart, newSlot.getDoctor().getId(), newSlot.getSlotDate(),
                newSlot.getStartTime(), newSlot.getEndTime()));
        return appointment;
    }

    // ================= Trạng thái khám (bác sĩ) =================

    /** YCCN-16: bác sĩ phụ trách chuyển IN_PROGRESS hoặc COMPLETED theo đúng thứ tự. */
    @Transactional
    public AppointmentDTO updateStatus(Long appointmentId, AppointmentStatus target) {
        if (target != AppointmentStatus.IN_PROGRESS && target != AppointmentStatus.COMPLETED) {
            throw new FieldValidationException("status", "Bác sĩ chỉ được chuyển sang IN_PROGRESS hoặc COMPLETED");
        }
        return mapper.toDTO(transitionByCurrentDoctor(appointmentId, target));
    }

    /** API nội bộ cho luồng khám (Dev 4): bắt đầu khám (BOOKED → IN_PROGRESS) bởi bác sĩ phụ trách. */
    @Transactional
    public Appointment startExamination(Long appointmentId) {
        return transitionByCurrentDoctor(appointmentId, AppointmentStatus.IN_PROGRESS);
    }

    /** API nội bộ cho luồng khám (Dev 4): hoàn thành khám (IN_PROGRESS → COMPLETED) bởi bác sĩ phụ trách. */
    @Transactional
    public Appointment completeExamination(Long appointmentId) {
        return transitionByCurrentDoctor(appointmentId, AppointmentStatus.COMPLETED);
    }

    // ================= Đọc =================

    /**
     * YCCN-15/19: lịch của người đang đăng nhập — bác sĩ (có hồ sơ bác sĩ) thấy lịch khám của mình,
     * bệnh nhân thấy lịch của mình. {@code order} là {@code asc} hoặc {@code desc} theo ngày giờ khám.
     */
    @Transactional(readOnly = true)
    public PageResponse<AppointmentDTO> getMyAppointments(
            AppointmentStatus status, LocalDate from, LocalDate to, String order, int page, int size) {
        CurrentUser user = currentUserService.requireRole("PATIENT", "DOCTOR");

        Specification<Appointment> spec;
        if (user.hasRole("DOCTOR") && currentActorService.findCurrentDoctorId().isPresent()) {
            Long doctorId = currentActorService.requireCurrentDoctorId();
            spec = (root, query, cb) -> cb.equal(root.get("doctor").get("id"), doctorId);
        } else {
            Long patientId = currentActorService.requireCurrentPatientId();
            spec = (root, query, cb) -> cb.equal(root.get("patientId"), patientId);
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("slot").get("slotDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("slot").get("slotDate"), to));
        }

        Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(direction, "slot.slotDate").and(Sort.by(direction, "slot.startTime"))
                        .and(Sort.by(direction, "id"))); // khóa chính: hai lịch cùng giờ không lặp/sót giữa các trang

        Page<Appointment> result = appointmentRepository.findAll(spec, pageable);
        return new PageResponse<>(mapper.toDTOs(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /** Chi tiết một lịch hẹn: chủ lịch hoặc bác sĩ phụ trách; người khác (kể cả Admin) → 403. */
    @Transactional(readOnly = true)
    public AppointmentDTO getById(Long id) {
        CurrentUser user = currentUserService.requireCurrentUser();
        Appointment appointment = findById(id);

        boolean owner = user.hasRole("PATIENT")
                && currentActorService.findCurrentPatientId().map(appointment.getPatientId()::equals).orElse(false);
        boolean doctor = user.hasRole("DOCTOR")
                && currentActorService.findCurrentDoctorId().map(appointment.getDoctorId()::equals).orElse(false);
        if (!owner && !doctor) {
            throw new ForbiddenException("Bạn không có quyền xem lịch hẹn này!");
        }
        return mapper.toDTO(appointment);
    }

    // ================= Nội bộ cho module khác (không kiểm quyền người gọi) =================

    @Transactional(readOnly = true)
    public Appointment findById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn!"));
    }

    /** @deprecated dùng {@link #findById(Long)}; giữ lại để {@code EncounterService} cũ biên dịch được. */
    @Deprecated
    @Transactional(readOnly = true)
    public Appointment getAppointmentById(Long id) {
        return findById(id);
    }

    // ================= Chi tiết xử lý =================

    private Appointment transitionByCurrentDoctor(Long appointmentId, AppointmentStatus target) {
        currentUserService.requireRole("DOCTOR");
        Long doctorId = currentActorService.requireCurrentDoctorId();
        Appointment appointment = findById(appointmentId);

        if (!doctorId.equals(appointment.getDoctorId())) {
            throw new ForbiddenException("Bác sĩ không phụ trách lịch khám này!");
        }
        if (!appointment.getStatus().canTransitionTo(target)) {
            throw new UnprocessableEntityException("Không thể chuyển lịch khám từ " + appointment.getStatus()
                    + " sang " + target + ". Thứ tự hợp lệ: BOOKED → IN_PROGRESS → COMPLETED.");
        }
        appointment.setStatus(target);
        return appointmentRepository.saveAndFlush(appointment);
    }

    private Appointment requireOwnedByPatient(Long appointmentId, Long patientId) {
        Appointment appointment = findById(appointmentId);
        if (!patientId.equals(appointment.getPatientId())) {
            throw new ForbiddenException("Bạn không có quyền thao tác trên lịch hẹn này!");
        }
        return appointment;
    }

    /** Bệnh nhân chỉ hủy/đổi được lịch BOOKED và còn đủ {@code cancel-before-hours} trước giờ khám. */
    private void assertChangeableByPatient(Appointment appointment, String action) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new UnprocessableEntityException("Chỉ có thể " + action + " lịch hẹn ở trạng thái BOOKED (hiện tại: "
                    + appointment.getStatus() + ")!");
        }
        LocalDateTime start = LocalDateTime.of(appointment.getSlot().getSlotDate(), appointment.getSlot().getStartTime());
        if (Duration.between(LocalDateTime.now(), start).compareTo(Duration.ofHours(cancelBeforeHours)) < 0) {
            throw new ConflictException("Không thể " + action + " lịch trong vòng " + cancelBeforeHours
                    + " giờ trước giờ khám. Vui lòng liên hệ phòng khám!");
        }
    }

    /** Tại quầy: chỉ lịch BOOKED (sai trạng thái → 422) và chưa qua giờ bắt đầu (đã qua → 409). */
    private void assertChangeableAtDesk(Appointment appointment, String action) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new UnprocessableEntityException("Chỉ có thể " + action + " lịch hẹn ở trạng thái BOOKED (hiện tại: "
                    + appointment.getStatus() + ")!");
        }
        LocalDateTime start = LocalDateTime.of(appointment.getSlot().getSlotDate(), appointment.getSlot().getStartTime());
        if (!start.isAfter(LocalDateTime.now())) {
            throw new ConflictException("Lịch hẹn đã qua giờ bắt đầu nên không thể " + action + "!");
        }
    }

    private void assertSlotBookable(AppointmentSlot slot, String label) {
        boolean available = Boolean.TRUE.equals(slot.getIsAvailable()) && "AVAILABLE".equalsIgnoreCase(slot.getStatus());
        if (!available) {
            throw new ConflictException(label + " đã được đặt hoặc không còn khả dụng!");
        }
        if (!Boolean.TRUE.equals(slot.getDoctor().getIsActive())) {
            throw new ConflictException("Bác sĩ của " + label.toLowerCase(Locale.ROOT) + " hiện không nhận lịch!");
        }
        if (LocalDateTime.of(slot.getSlotDate(), slot.getStartTime()).isBefore(LocalDateTime.now())) {
            throw new ConflictException(label + " đã qua!");
        }
    }

    /** Thời lượng dịch vụ dài hơn khung giờ → 422. Độ dài khung giờ tính từ giờ kết thúc trừ giờ bắt đầu. */
    private static void assertServiceFits(MedicalService service, AppointmentSlot slot) {
        long slotMinutes = Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes();
        Integer needed = service.getDurationMinutes();
        if (needed != null && needed > slotMinutes) {
            throw new UnprocessableEntityException("Dịch vụ cần " + needed + " phút, khung giờ này chỉ dài "
                    + slotMinutes + " phút");
        }
    }

    /** Chiếm slot: đổi sang BOOKED và flush để khóa lạc quan {@code @Version} phát hiện xung đột ngay. */
    private void takeSlot(AppointmentSlot slot, String conflictMessage) {
        slot.setIsAvailable(false);
        slot.setStatus("BOOKED");
        try {
            slotRepository.saveAndFlush(slot);
        } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
            throw new ConflictException(conflictMessage);
        }
    }

    private void releaseSlot(AppointmentSlot slot) {
        slot.setIsAvailable(true);
        slot.setStatus("AVAILABLE");
        slotRepository.save(slot);
    }

    private void flushOrConflict(String message) {
        try {
            appointmentRepository.flush();
        } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
            throw new ConflictException(message);
        }
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private MedicalService requireActiveService(Long serviceId) {
        MedicalService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new FieldValidationException("serviceId", "Dịch vụ khám không tồn tại"));
        if (!"ACTIVE".equalsIgnoreCase(service.getStatus())) {
            throw new FieldValidationException("serviceId", "Dịch vụ khám đã ngừng sử dụng");
        }
        return service;
    }

}
