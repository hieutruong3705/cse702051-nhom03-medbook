package com.phenikaa.cse702051.medbook.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo lịch khám theo ngày khám trong {@code [from, to]}. Luôn đúng: {@code total = booked + inProgress +
 * completed + cancelled}, và tổng {@code total} của các nhóm bằng {@code total} chung.
 * {@code cancellationRate} là phần trăm (0–100), hai chữ số thập phân, bằng 0 khi không có lịch nào.
 */
public record AppointmentReportDTO(
        LocalDate from,
        LocalDate to,
        String groupBy,
        long total,
        long booked,
        long inProgress,
        long completed,
        long cancelled,
        BigDecimal cancellationRate,
        List<Group> groups
) {

    /** Một nhóm của báo cáo: một ngày, một bác sĩ hoặc một chuyên khoa. */
    public record Group(
            String key,
            String label,
            long total,
            long completed,
            long cancelled,
            BigDecimal cancellationRate
    ) {
    }
}
