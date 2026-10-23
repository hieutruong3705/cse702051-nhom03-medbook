package com.phenikaa.cse702051.medbook.dto.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.DoctorDayOff;

/** Ngày nghỉ của bác sĩ. */
public record DayOffDTO(
        Long id,
        LocalDate date,
        String reason,
        LocalDateTime createdAt
) {
    public static DayOffDTO from(DoctorDayOff dayOff) {
        return new DayOffDTO(dayOff.getId(), dayOff.getOffDate(), dayOff.getReason(), dayOff.getCreatedAt());
    }
}
