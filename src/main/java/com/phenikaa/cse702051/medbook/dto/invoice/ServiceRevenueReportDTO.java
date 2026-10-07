package com.phenikaa.cse702051.medbook.dto.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo dịch vụ khám: mỗi dịch vụ được dùng bao nhiêu lần và mang lại bao nhiêu giá trị trong các hóa đơn LẬP
 * trong {@code [from, to]} (không tính hóa đơn đã hủy). Giá trị là tổng thành tiền của các dòng hóa đơn, trước
 * giảm giá của cả hóa đơn, nên tổng ở đây có thể lớn hơn giá trị lập hóa đơn của báo cáo doanh thu.
 */
public record ServiceRevenueReportDTO(
        LocalDate from,
        LocalDate to,
        BigDecimal totalQuantity,
        BigDecimal totalAmount,
        List<Row> services
) {

    /**
     * Một dịch vụ, xếp theo giá trị giảm dần. {@code sharePercent} là tỷ trọng giá trị của dịch vụ trong tổng (hai
     * chữ số thập phân). {@code serviceId} rỗng với dòng hóa đơn không gắn dịch vụ trong danh mục.
     */
    public record Row(
            Long serviceId,
            String serviceCode,
            String serviceName,
            long invoiceCount,
            BigDecimal quantity,
            BigDecimal amount,
            BigDecimal sharePercent
    ) {
    }
}
