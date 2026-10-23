package com.phenikaa.cse702051.medbook.dto.invoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Invoice;

/** Một dòng trong danh sách hóa đơn của bệnh nhân. */
public record InvoiceSummaryDTO(
        Long id,
        String invoiceCode,
        BigDecimal totalAmount,
        String status,
        LocalDateTime issuedAt,
        LocalDateTime paidAt
) {
    public static InvoiceSummaryDTO from(Invoice invoice) {
        return new InvoiceSummaryDTO(invoice.getId(), invoice.getInvoiceCode(), invoice.getTotalAmount(),
                invoice.getStatus(), invoice.getIssuedAt(), invoice.getPaidAt());
    }
}
