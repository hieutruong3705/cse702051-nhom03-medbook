package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.AppointmentSlotDTO;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.DoctorSchedule;
import com.phenikaa.cse702051.medbook.model.ScheduleBreak;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorScheduleRepository;
import com.phenikaa.cse702051.medbook.repository.ScheduleBreakRepository;

/**
 * Khung giờ trống mà bệnh nhân nhìn thấy khi đặt lịch (YCCN-09/10).
 */
@Service
public class AppointmentSlotService {

    private final AppointmentSlotRepository appointmentSlotRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final ScheduleBreakRepository breakRepository;

    public AppointmentSlotService(
            AppointmentSlotRepository appointmentSlotRepository,
            DoctorRepository doctorRepository,
            DoctorScheduleRepository scheduleRepository,
            ScheduleBreakRepository breakRepository) {
        this.appointmentSlotRepository = appointmentSlotRepository;
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
        this.breakRepository = breakRepository;
    }

    /**
     * Slot còn trống của một bác sĩ trong một ngày, theo giờ tăng dần. Chỉ trả slot đặt được thật sự: bác sĩ phải
     * đang hoạt động (nếu không → 404), slot ở trạng thái AVAILABLE, chưa qua giờ bắt đầu (khi là hôm nay) và
     * không chạm giờ nghỉ. Ngày đã qua → danh sách rỗng. Không đọc gì từ người gọi nên dùng được cho khách.
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

        return appointmentSlotRepository.findByDoctorIdAndSlotDateAndIsAvailableTrue(doctorId, day).stream()
                .filter(slot -> "AVAILABLE".equalsIgnoreCase(slot.getStatus()))
                .filter(slot -> notBefore == null || slot.getStartTime().isAfter(notBefore))
                .filter(slot -> breaks.stream().noneMatch(b ->
                        slot.getStartTime().isBefore(b.getEndTime()) && slot.getEndTime().isAfter(b.getStartTime())))
                .sorted(Comparator.comparing(AppointmentSlot::getStartTime))
                .map(slot -> new AppointmentSlotDTO(slot.getId(), doctorId, slot.getSlotDate(), slot.getStartTime(),
                        slot.getEndTime(), slot.getStatus()))
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
