package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.DoctorSchedule;
import com.phenikaa.cse702051.medbook.model.ScheduleBreak;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorScheduleRepository;
import com.phenikaa.cse702051.medbook.repository.ScheduleBreakRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorScheduleService {

    private final DoctorScheduleRepository scheduleRepository;
    private final ScheduleBreakRepository breakRepository;
    private final AppointmentSlotRepository slotRepository;
    private final DoctorRepository doctorRepository;

    /**
     * YCCN 14: Tạo ca làm việc và tự động sinh các slot khám hợp lệ
     * Slot khám mặc định dài 30 phút, tự loại trừ các khoảng thời gian trùng ScheduleBreak
     */
    @Transactional
    public DoctorSchedule createScheduleAndGenerateSlots(
            Long doctorId,
            LocalDate workDate,
            LocalTime startTime,
            LocalTime endTime,
            int slotDurationMinutes) {

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bác sĩ"));

        if (startTime.isAfter(endTime) || startTime.equals(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ bắt đầu phải trước giờ kết thúc!");
        }

        DoctorSchedule schedule = DoctorSchedule.builder()
                .doctor(doctor)
                .workDate(workDate)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        schedule = scheduleRepository.save(schedule);

        // Lấy danh sách giờ nghỉ đã khai báo cho ca này (nếu có)
        List<ScheduleBreak> breaks = breakRepository.findByDoctorScheduleId(schedule.getId());

        // Thuật toán chia ca thành các slot
        List<AppointmentSlot> generatedSlots = new ArrayList<>();
        LocalTime current = startTime;

        while (current.plusMinutes(slotDurationMinutes).isBefore(endTime)
                || current.plusMinutes(slotDurationMinutes).equals(endTime)) {
            LocalTime next = current.plusMinutes(slotDurationMinutes);

            // Kiểm tra xem khoảng [current, next] có trùng với giờ nghỉ nào không
            final LocalTime slotStart = current;
            final LocalTime slotEnd = next;
            boolean isBreak = breaks.stream().anyMatch(b ->
                    (slotStart.isBefore(b.getEndTime()) && slotEnd.isAfter(b.getStartTime()))
            );

            if (!isBreak) {
                AppointmentSlot slot = AppointmentSlot.builder()
                        .doctor(doctor)
                        .slotDate(workDate)
                        .startTime(slotStart)
                        .endTime(slotEnd)
                        .isAvailable(true)
                        .status("AVAILABLE")
                        .build();
                generatedSlots.add(slot);
            }

            current = next;
        }

        slotRepository.saveAll(generatedSlots);
        return schedule;
    }

    public List<DoctorSchedule> getSchedulesByDoctor(Long doctorId) {
        return scheduleRepository.findByDoctorId(doctorId);
    }
}