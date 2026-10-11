package com.phenikaa.cse702051.medbook.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.AppointmentSlotDTO;
import com.phenikaa.cse702051.medbook.dto.slot.EarliestSlotDTO;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.DoctorSchedule;
import com.phenikaa.cse702051.medbook.model.MedicalService;
import com.phenikaa.cse702051.medbook.model.ScheduleBreak;
import com.phenikaa.cse702051.medbook.model.SlotHold;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorScheduleRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.repository.ScheduleBreakRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Khung giờ trống mà bệnh nhân nhìn thấy khi đặt lịch (YCCN-09/10).
 *
 * <p>Danh sách phụ thuộc cả bác sĩ lẫn loại dịch vụ: có dịch vụ thì chỉ còn khung giờ đủ dài cho thời lượng của dịch
 * vụ đó. Độ dài khung giờ tính từ giờ kết thúc trừ giờ bắt đầu (do ca làm việc quyết định), không có cột riêng.
 * Giới hạn đã biết: một dịch vụ dài hơn khung giờ không được ghép từ nhiều khung giờ liền nhau, vì một lịch hẹn
 * gắn với đúng một khung giờ.
 */
@Service
public class AppointmentSlotService {

    private final AppointmentSlotRepository appointmentSlotRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final ScheduleBreakRepository breakRepository;
    private final SlotHoldService slotHoldService;
    private final CurrentUserService currentUserService;
    private final MedicalServiceRepository serviceRepository;

    private static final int EARLIEST_DEFAULT_LIMIT = 5;
    private static final int EARLIEST_MAX_LIMIT = 20;
    private static final int EARLIEST_WINDOW_DAYS = 30;

    public AppointmentSlotService(
            AppointmentSlotRepository appointmentSlotRepository,
            DoctorRepository doctorRepository,
            DoctorScheduleRepository scheduleRepository,
            ScheduleBreakRepository breakRepository,
            SlotHoldService slotHoldService,
            CurrentUserService currentUserService,
            MedicalServiceRepository serviceRepository) {
        this.appointmentSlotRepository = appointmentSlotRepository;
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
        this.breakRepository = breakRepository;
        this.slotHoldService = slotHoldService;
        this.currentUserService = currentUserService;
        this.serviceRepository = serviceRepository;
    }

    /**
     * Slot còn trống của một bác sĩ trong một ngày, theo giờ tăng dần. Chỉ trả slot đặt được thật sự: bác sĩ phải
     * đang hoạt động (nếu không → 404), slot ở trạng thái AVAILABLE, chưa qua giờ bắt đầu (khi là hôm nay) và
     * không chạm giờ nghỉ. Ngày đã qua → danh sách rỗng.
     *
     * <p>Khung giờ người khác đang giữ chỗ còn hạn bị ẩn; khung giờ do chính người xem đang giữ vẫn hiện, kèm
     * {@code heldByMe} và {@code holdExpiresAt}. Khách chưa đăng nhập không giữ gì nên chỉ thấy khung giờ không ai giữ.
     */
    @Transactional(readOnly = true)
    public List<AppointmentSlotDTO> getAvailableSlots(Long doctorId, LocalDate slotDate) {
        return getAvailableSlots(doctorId, slotDate, null);
    }

    /**
     * Như {@link #getAvailableSlots(Long, LocalDate)}; có {@code serviceId} thì chỉ trả khung giờ có độ dài lớn hơn
     * hoặc bằng thời lượng của dịch vụ. Dịch vụ không tồn tại hoặc đã ngừng → 400 kèm {@code details.serviceId}.
     */
    @Transactional(readOnly = true)
    public List<AppointmentSlotDTO> getAvailableSlots(Long doctorId, LocalDate slotDate, Long serviceId) {
        int neededMinutes = serviceId == null ? 0 : requireActiveService(serviceId).getDurationMinutes();
        doctorRepository.findById(doctorId)
                .filter(doctor -> Boolean.TRUE.equals(doctor.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bác sĩ!"));

        LocalDate today = LocalDate.now();
        LocalDate day = slotDate != null ? slotDate : today;
        if (day.isBefore(today)) {
            return List.of();
        }
        LocalTime notBefore = day.equals(today) ? LocalTime.now() : null;
        List<ScheduleBreak> breaks = breaksOfDay(doctorId, day);

        List<AppointmentSlot> free = appointmentSlotRepository
                .findByDoctorIdAndSlotDateAndIsAvailableTrue(doctorId, day).stream()
                .filter(slot -> "AVAILABLE".equalsIgnoreCase(slot.getStatus()))
                .filter(slot -> notBefore == null || slot.getStartTime().isAfter(notBefore))
                .filter(slot -> Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes() >= neededMinutes)
                .filter(slot -> breaks.stream().noneMatch(b ->
                        slot.getStartTime().isBefore(b.getEndTime()) && slot.getEndTime().isAfter(b.getStartTime())))
                .sorted(Comparator.comparing(AppointmentSlot::getStartTime))
                .toList();

        Map<Long, SlotHold> holds = slotHoldService.activeHolds(free.stream().map(AppointmentSlot::getId).toList());
        Long viewerId = currentUserService.findCurrentUser().map(CurrentUser::userId).orElse(null);
        return free.stream()
                .filter(slot -> !holds.containsKey(slot.getId())
                        || holds.get(slot.getId()).getUserId().equals(viewerId))
                .map(slot -> {
                    SlotHold mine = holds.get(slot.getId());
                    return new AppointmentSlotDTO(slot.getId(), doctorId, slot.getSlotDate(), slot.getStartTime(),
                            slot.getEndTime(), slot.getStatus(), mine != null,
                            mine == null ? null : mine.getExpiresAt());
                })
                .toList();
    }

    /**
     * Các khung giờ sớm nhất đặt được cho một dịch vụ, qua mọi bác sĩ đang nhận lịch (hoặc chỉ một chuyên khoa, một
     * bác sĩ), trong 30 ngày kể từ {@code from} (mặc định và sớm nhất là hôm nay). Chỉ lấy khung giờ còn trống, chưa
     * qua, đủ dài cho dịch vụ, không chạm giờ nghỉ và không bị người khác giữ chỗ. {@code limit} mặc định 5, tối đa 20.
     */
    @Transactional(readOnly = true)
    public List<EarliestSlotDTO> findEarliest(Long serviceId, Long specialtyId, Long doctorId, LocalDate from,
            Integer limit) {
        Long viewerId = currentUserService.requireRole("PATIENT", "RECEPTIONIST").userId();
        if (serviceId == null) {
            throw new FieldValidationException("serviceId", "Phải chọn dịch vụ khám");
        }
        MedicalService service = requireActiveService(serviceId);

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        // Mốc bắt đầu là một thời điểm: hôm nay thì từ giờ hiện tại, ngày sau đó thì từ đầu ngày.
        boolean startsToday = from == null || !from.isAfter(today);
        LocalDate firstDay = startsToday ? today : from;
        LocalTime fromTime = startsToday ? now.toLocalTime() : LocalTime.MIN;
        int size = limit == null ? EARLIEST_DEFAULT_LIMIT : Math.max(1, Math.min(limit, EARLIEST_MAX_LIMIT));

        return appointmentSlotRepository.findEarliest(service.getDurationMinutes(), firstDay, fromTime,
                firstDay.plusDays(EARLIEST_WINDOW_DAYS), doctorId, specialtyId, viewerId, now,
                PageRequest.of(0, size));
    }

    private MedicalService requireActiveService(Long serviceId) {
        MedicalService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new FieldValidationException("serviceId", "Dịch vụ khám không tồn tại"));
        if (!"ACTIVE".equalsIgnoreCase(service.getStatus())) {
            throw new FieldValidationException("serviceId", "Dịch vụ khám đã ngừng sử dụng");
        }
        return service;
    }

    private List<ScheduleBreak> breaksOfDay(Long doctorId, LocalDate day) {
        List<Long> scheduleIds = scheduleRepository.findByDoctorIdAndWorkDate(doctorId, day).stream()
                .map(DoctorSchedule::getId)
                .toList();
        if (scheduleIds.isEmpty()) {
            return List.of();
        }
        return breakRepository.findByDoctorScheduleIdIn(scheduleIds);
    }
}
