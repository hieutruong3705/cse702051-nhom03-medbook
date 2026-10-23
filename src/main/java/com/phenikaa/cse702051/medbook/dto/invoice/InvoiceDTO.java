package com.phenikaa.cse702051.medbook.dto.invoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.phenikaa.cse702051.medbook.model.Invoice;

/**
 * Hóa đơn đầy đủ kèm các dòng dịch vụ. {@code totalAmount = subtotal − discountAmount}. {@code status}:
 * {@code UNPAID} (đã lập, chưa thu), {@code PAID} (đã thu tại quầy), {@code VOID} (đã hủy).
 */
public record InvoiceDTO(
        Long id,
        String invoiceCode,
        Long patientId,
        String patientName,
        Long appointmentId,
        Long encounterId,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String status,
        LocalDateTime issuedAt,
        LocalDateTime paidAt,
        LocalDateTime voidedAt,
        String voidReason,
        List<InvoiceItemDTO> items
) {
    public static InvoiceDTO from(Invoice invoice, String patientName, Long encounterId,
            List<InvoiceItemDTO> items) {
        return new InvoiceDTO(invoice.getId(), invoice.getInvoiceCode(), invoice.getPatientId(), patientName,
                invoice.getAppointmentId(), encounterId, invoice.getSubtotal(), invoice.getDiscountAmount(),
                invoice.getTotalAmount(), invoice.getStatus(), invoice.getIssuedAt(), invoice.getPaidAt(),
                invoice.getVoidedAt(), invoice.getVoidReason(), items);
    }
}
