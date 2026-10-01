package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Sửa giờ của ca (chỉ khi chưa có lịch hẹn nào giữ chỗ trong ca). Trường {@code null} = giữ nguyên.
 * Không đổi được ngày làm việc: muốn đổi ngày hãy xóa ca rồi tạo lại.
 */
public record UpdateScheduleRequest(
        LocalTime startTime,

        LocalTime endTime,

        @Min(value = 5, message = "Mỗi slot tối thiểu 5 phút")
        @Max(value = 240, message = "Mỗi slot tối đa 240 phút")
        Integer slotMinutes
) {
}
