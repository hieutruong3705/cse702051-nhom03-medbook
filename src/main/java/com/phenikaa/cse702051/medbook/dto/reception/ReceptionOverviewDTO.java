package com.phenikaa.cse702051.medbook.dto.reception;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Số liệu trong ngày cho trang tổng quan của quầy lễ tân. Bốn số đếm lịch hẹn tính theo ngày khám {@code date}; hóa
 * đơn chưa thu tính trên mọi hóa đơn đang chưa thu; hóa đơn đã thu tính theo thời điểm thu rơi vào ngày {@code date}.
 */
public record ReceptionOverviewDTO(
        LocalDate date,
        long bookedCount,
        long inProgressCount,
        long completedCount,
        long cancelledCount,
        long unpaidInvoiceCount,
        BigDecimal unpaidInvoiceAmount,
        long collectedInvoiceCount,
        BigDecimal collectedAmount
) {
}
