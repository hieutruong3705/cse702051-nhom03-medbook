package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.AppointmentSlotDTO;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.DoctorSchedule;
import com.phenikaa.cse702051.medbook.model.ScheduleBreak;
import com.phenikaa.cse702051.medbook.model.SlotHold;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorScheduleRepository;
import com.phenikaa.cse702051.medbook.repository.ScheduleBreakRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Khung giờ trống mà bệnh nhân nhìn thấy khi đặt lịch (YCCN-09/10).
 */
@Service
public class AppointmentSlotService {

    private final AppointmentSlotRepository appointmentSlotRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final ScheduleBreakRepository breakRepository;
    private final SlotHoldService slotHoldService;
    private final CurrentUserService currentUserService;

    public AppointmentSlotService(
            AppointmentSlotRepository appointmentSlotRepository,
            DoctorRepository doctorRepository,
            DoctorScheduleRepository scheduleRepository,
            ScheduleBreakRepository breakRepository,
            SlotHoldService slotHoldService,
            CurrentUserService currentUserService) {
        this.appointmentSlotRepository = appointmentSlotRepository;
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
        this.breakRepository = breakRepository;
        this.slotHoldService = slotHoldService;
        this.currentUserService = currentUserService;
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
