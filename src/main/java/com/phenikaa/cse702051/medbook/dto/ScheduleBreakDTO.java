package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalTime;

/** Giờ nghỉ trong một ca làm việc. */
public record ScheduleBreakDTO(
        Long id,
        LocalTime startTime,
        LocalTime endTime,
        String reason
) {
}
