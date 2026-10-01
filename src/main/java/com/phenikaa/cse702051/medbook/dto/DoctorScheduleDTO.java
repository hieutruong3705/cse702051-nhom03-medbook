package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Ca làm việc của bác sĩ. {@code slotMinutes} suy ra từ các slot đã sinh (bảng ca không lưu cột này nên
 * không đổi schema); {@code null} khi ca không còn slot nào. {@code totalSlots} không tính slot đã bị gỡ,
 * {@code bookedSlots} là số slot đang có lịch hẹn giữ chỗ.
 */
public record DoctorScheduleDTO(
        Long id,
        Long doctorId,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer slotMinutes,
        List<ScheduleBreakDTO> breaks,
        int totalSlots,
        int bookedSlots
) {
}
