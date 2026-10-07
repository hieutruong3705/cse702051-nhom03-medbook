package com.phenikaa.cse702051.medbook.dto.invoice;

import java.math.BigDecimal;

import com.phenikaa.cse702051.medbook.model.InvoiceItem;

/**
 * Một dòng hóa đơn. {@code description} và {@code unitPrice} là tên và đơn giá của dịch vụ tại thời điểm lập
 * hóa đơn (không đổi khi danh mục đổi giá); {@code lineTotal = quantity × unitPrice}.
 */
public record InvoiceItemDTO(
        Long id,
        Long serviceId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
    public static InvoiceItemDTO from(InvoiceItem item) {
        return new InvoiceItemDTO(item.getId(), item.getServiceId(), item.getDescription(), item.getQuantity(),
                item.getUnitPrice(), item.getLineTotal());
    }
}
