package com.phenikaa.cse702051.medbook.dto.slot;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Một gợi ý "giờ sớm nhất còn trống" cho dịch vụ đã chọn: đủ thông tin để giao diện điền sẵn bác sĩ, ngày và khung
 * giờ. {@code specialtyId} và {@code specialtyName} là {@code null} khi bác sĩ chưa gắn chuyên khoa.
 */
public record EarliestSlotDTO(
        Long slotId,
        Long doctorId,
        String doctorName,
        Long specialtyId,
        String specialtyName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {
}
