package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.schedule.DayOffDTO;
import com.phenikaa.cse702051.medbook.dto.schedule.DayOffRequest;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.exception.UnprocessableEntityException;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.DoctorDayOff;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorDayOffRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Ngày nghỉ của bác sĩ. Đăng ký nghỉ một ngày gỡ mọi giờ trống của ngày đó (ca làm việc vẫn giữ nguyên); xóa ngày
 * nghỉ sinh lại giờ trống theo các ca đang có. Ngày đã có lịch hẹn được đặt thì không đăng ký nghỉ được (409): bác
 * sĩ phải xử lý các lịch hẹn đó trước, hệ thống không tự hủy lịch của bệnh nhân.
 *
 * <p>Bác sĩ luôn lấy từ JWT. Thêm và xóa ngày nghỉ khóa dòng bác sĩ như mọi thay đổi lịch làm việc khác (xem
 * {@link DoctorScheduleService#lockCurrentDoctor()}), và việc sinh/gỡ slot đi qua
 * {@link DoctorScheduleService#rebuildDay}.
 */
@Service
public class DoctorDayOffService {

    static final int MAX_LIST_DAYS = 366;

    private final DoctorDayOffRepository dayOffRepository;
    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorScheduleService scheduleService;
    private final CurrentUserService currentUserService;
    private final CurrentActorService currentActorService;
    private final AuditLogService auditLogService;

    public DoctorDayOffService(
            DoctorDayOffRepository dayOffRepository,
            AppointmentSlotRepository slotRepository,
            AppointmentRepository appointmentRepository,
            DoctorScheduleService scheduleService,
            CurrentUserService currentUserService,
            CurrentActorService currentActorService,
            AuditLogService auditLogService) {
        this.dayOffRepository = dayOffRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.scheduleService = scheduleService;
        this.currentUserService = currentUserService;
        this.currentActorService = currentActorService;
        this.auditLogService = auditLogService;
    }

    /**
     * Ngày nghỉ của bác sĩ đang đăng nhập, theo ngày tăng dần. Mặc định từ hôm nay; thiếu {@code to} thì lấy đủ
     * {@value #MAX_LIST_DAYS} ngày kể từ {@code from}; khoảng dài hơn → 400.
     */
    @Transactional(readOnly = true)
    public List<DayOffDTO> listMine(LocalDate from, LocalDate to) {
        currentUserService.requireRole("DOCTOR");
        Long doctorId = currentActorService.requireCurrentDoctorId();
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusDays(MAX_LIST_DAYS - 1L);
        if (end.isBefore(start)) {
            throw new FieldValidationException("to", "Ngày kết thúc phải sau ngày bắt đầu");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_LIST_DAYS) {
            throw new FieldValidationException("to", "Chỉ xem được tối đa " + MAX_LIST_DAYS + " ngày mỗi lần");
        }
        return dayOffRepository.findByDoctorIdAndOffDateBetweenOrderByOffDateAsc(doctorId, start, end).stream()
                .map(DayOffDTO::from)
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DayOffDTO create(DayOffRequest request) {
        Doctor doctor = scheduleService.lockCurrentDoctor();
        LocalDate date = request.date();
        if (date.isBefore(LocalDate.now())) {
            throw new FieldValidationException("date", "Không thể đăng ký nghỉ cho ngày đã qua");
        }
        if (dayOffRepository.existsByDoctorIdAndOffDate(doctor.getId(), date)) {
            throw new UnprocessableEntityException("Bạn đã đăng ký nghỉ ngày " + date + " rồi!");
        }
        long booked = countActiveAppointments(doctor.getId(), date);
        if (booked > 0) {
            throw new ConflictException("Ngày " + date + " đã có " + booked
                    + " lịch hẹn được đặt. Hãy xử lý các lịch hẹn đó trước khi đăng ký nghỉ!");
        }

        DoctorDayOff dayOff;
        try {
            dayOff = dayOffRepository.saveAndFlush(DoctorDayOff.builder()
                    .doctor(doctor)
                    .offDate(date)
                    .reason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(doctor_id, off_date)
            throw new UnprocessableEntityException("Bạn đã đăng ký nghỉ ngày " + date + " rồi!");
        }
        scheduleService.rebuildDay(doctor.getId(), date);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.DAY_OFF_CREATE,
                AuditActions.ENTITY_DOCTOR_DAY_OFFS, dayOff.getId()).with("date", date.toString()));
        return DayOffDTO.from(dayOff);
    }

    /** Xóa ngày nghỉ và sinh lại giờ trống cho các ca của ngày đó. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id) {
        Doctor doctor = scheduleService.lockCurrentDoctor();
        DoctorDayOff dayOff = dayOffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngày nghỉ!"));
        if (!dayOff.getDoctor().getId().equals(doctor.getId())) {
            throw new ForbiddenException("Ngày nghỉ này không thuộc về bạn!");
        }
        LocalDate date = dayOff.getOffDate();
        dayOffRepository.delete(dayOff);
        dayOffRepository.flush();
        int slots = scheduleService.rebuildDay(doctor.getId(), date);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.DAY_OFF_DELETE,
                AuditActions.ENTITY_DOCTOR_DAY_OFFS, id).with("date", date.toString()).with("slots", slots));
    }

    private long countActiveAppointments(Long doctorId, LocalDate date) {
        List<Long> slotIds = slotRepository.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctorId, date).stream()
                .map(AppointmentSlot::getId)
                .toList();
        return slotIds.isEmpty() ? 0
                : appointmentRepository.findActiveBySlotIds(slotIds, AppointmentStatus.CANCELLED).size();
    }
}
