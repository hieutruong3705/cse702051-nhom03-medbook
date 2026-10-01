package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Tạo ca làm việc và tự sinh các slot khám. Bác sĩ lấy từ JWT, KHÔNG nhận từ request.
 */
public record CreateScheduleRequest(
        @NotNull(message = "Phải chọn ngày làm việc")
        LocalDate workDate,

        @NotNull(message = "Phải nhập giờ bắt đầu")
        LocalTime startTime,

        @NotNull(message = "Phải nhập giờ kết thúc")
        LocalTime endTime,

        @NotNull(message = "Phải nhập số phút mỗi slot")
        @Min(value = 5, message = "Mỗi slot tối thiểu 5 phút")
        @Max(value = 240, message = "Mỗi slot tối đa 240 phút")
        Integer slotMinutes,

        @Valid
        @Size(max = 20, message = "Tối đa 20 giờ nghỉ mỗi ca")
        List<BreakInput> breaks
) {

    public record BreakInput(
            @NotNull(message = "Phải nhập giờ bắt đầu nghỉ")
            LocalTime startTime,

            @NotNull(message = "Phải nhập giờ kết thúc nghỉ")
            LocalTime endTime,

            @Size(max = 255, message = "Lý do nghỉ tối đa 255 ký tự")
            String reason
    ) {
    }
}
