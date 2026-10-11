package com.phenikaa.cse702051.medbook.dto.reception;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.model.Patient;

/**
 * Một hóa đơn nhìn từ quầy lễ tân: đủ để tìm, đối chiếu và thu tiền. Không có lý do hủy (việc hủy và lý do thuộc về
 * Quản trị viên).
 */
public record ReceptionInvoiceDTO(
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
        LocalDateTime voidedAt
) {
    public static ReceptionInvoiceDTO from(Invoice invoice, Patient patient) {
        return new ReceptionInvoiceDTO(invoice.getId(), invoice.getInvoiceCode(), invoice.getPatientId(),
                patient == null ? null : patient.getFullName(),
                patient == null ? null : patient.getPatientCode(),
                invoice.getSubtotal(), invoice.getDiscountAmount(), invoice.getTotalAmount(), invoice.getStatus(),
                invoice.getIssuedAt(), invoice.getPaidAt(), invoice.getVoidedAt());
    }
}
