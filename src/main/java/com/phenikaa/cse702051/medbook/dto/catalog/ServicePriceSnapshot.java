package com.phenikaa.cse702051.medbook.dto.catalog;

import java.math.BigDecimal;

/**
 * Tên và đơn giá của một dịch vụ đang hoạt động tại thời điểm gọi, để module khác (hóa đơn) chép vào bản ghi của
 * mình. Đơn giá luôn có scale 2.
 */
public record ServicePriceSnapshot(
        Long serviceId,
        String name,
        BigDecimal price
) {
}
