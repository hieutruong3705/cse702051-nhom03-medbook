package com.phenikaa.cse702051.medbook.dto.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo doanh thu tính trên các hóa đơn LẬP trong {@code [from, to]} (theo thời điểm lập).
 *
 * <ul>
 * <li>{@code invoicedAmount}: tổng giá trị hóa đơn không bị hủy.</li>
 * <li>{@code collectedAmount}: phần đã thu ({@code PAID}); {@code unpaidAmount}: phần chưa thu ({@code UNPAID}).</li>
 * <li>{@code invoiceCount}: số hóa đơn không bị hủy; {@code voidCount}: số hóa đơn đã hủy.</li>
 * </ul>
 *
 * Luôn đúng cho tổng chung và từng nhóm: {@code invoicedAmount = collectedAmount + unpaidAmount}. Hóa đơn đã lập
 * không đồng nghĩa với đã thu, nên hai số này luôn được trả riêng.
 */
public record RevenueReportDTO(
        LocalDate from,
        LocalDate to,
        String groupBy,
        BigDecimal invoicedAmount,
        BigDecimal collectedAmount,
        BigDecimal unpaidAmount,
        long invoiceCount,
        long voidCount,
        List<Group> groups
) {

    /** Một ngày ({@code key} dạng yyyy-MM-dd) hoặc một tháng ({@code key} dạng yyyy-MM). */
    public record Group(
            String key,
            String label,
            BigDecimal invoicedAmount,
            BigDecimal collectedAmount,
            BigDecimal unpaidAmount,
            long invoiceCount,
            long voidCount
    ) {
    }
}
