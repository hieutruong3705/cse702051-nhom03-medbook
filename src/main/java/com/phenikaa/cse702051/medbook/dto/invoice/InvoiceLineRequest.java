package com.phenikaa.cse702051.medbook.dto.invoice;

import java.math.BigDecimal;

/**
 * Một dòng khi lập hóa đơn: dịch vụ và số lượng. Cố ý KHÔNG có đơn giá hay mô tả: cả hai luôn lấy từ danh mục
 * dịch vụ ở phía server, client gửi thêm cũng bị bỏ qua. {@code quantity} nhận số thập phân để giá trị như 1.5
 * bị từ chối rõ ràng thay vì bị cắt thành 1.
 */
public record InvoiceLineRequest(
        Long serviceId,
        BigDecimal quantity
) {
}
