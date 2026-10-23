package com.phenikaa.cse702051.medbook.dto.reception;

import java.math.BigDecimal;

import com.phenikaa.cse702051.medbook.model.InvoiceItem;

/**
 * Một dòng hóa đơn cho quầy lễ tân. {@code description} và {@code unitPrice} là tên và đơn giá của dịch vụ tại thời
 * điểm lập hóa đơn; {@code lineTotal = quantity × unitPrice}.
 */
public record ReceptionInvoiceItemDTO(
        Long id,
        Long serviceId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
    public static ReceptionInvoiceItemDTO from(InvoiceItem item) {
        return new ReceptionInvoiceItemDTO(item.getId(), item.getServiceId(), item.getDescription(),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
    }
}
