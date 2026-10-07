package com.phenikaa.cse702051.medbook.dto.invoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.model.Patient;

/** Một dòng trong danh sách hóa đơn của trang quản trị: thêm tên và mã bệnh nhân, thông tin hủy. */
public record AdminInvoiceDTO(
        Long id,
        String invoiceCode,
        Long patientId,
        String patientName,
        String patientCode,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String status,
        LocalDateTime issuedAt,
        LocalDateTime paidAt,
        LocalDateTime voidedAt,
        String voidReason
) {
    public static AdminInvoiceDTO from(Invoice invoice, Patient patient) {
        return new AdminInvoiceDTO(invoice.getId(), invoice.getInvoiceCode(), invoice.getPatientId(),
                patient == null ? null : patient.getFullName(),
                patient == null ? null : patient.getPatientCode(),
                invoice.getSubtotal(), invoice.getDiscountAmount(), invoice.getTotalAmount(), invoice.getStatus(),
                invoice.getIssuedAt(), invoice.getPaidAt(), invoice.getVoidedAt(), invoice.getVoidReason());
    }
}
