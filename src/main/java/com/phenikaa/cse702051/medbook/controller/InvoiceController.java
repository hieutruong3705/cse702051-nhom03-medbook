package com.phenikaa.cse702051.medbook.controller;

import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.model.InvoiceItem;
import com.phenikaa.cse702051.medbook.service.InvoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(
            InvoiceService invoiceService
    ) {
        this.invoiceService = invoiceService;
    }

    /**
     * Tạo hóa đơn cho encounter.
     *
     * POST /api/encounters/{id}/invoice
     */
    @PostMapping("/encounters/{id}/invoice")
    public ResponseEntity<Invoice> createInvoice(
            @PathVariable Long id,
            @RequestParam(
                    required = false,
                    defaultValue = "0"
            ) BigDecimal discountAmount,
            @RequestBody List<InvoiceItem> items
    ) {
        Invoice invoice =
                invoiceService.createInvoice(
                        id,
                        items,
                        discountAmount
                );

        return ResponseEntity.ok(invoice);
    }

    /**
     * Xem hóa đơn của một lần khám (bệnh nhân chủ lần khám hoặc bác sĩ phụ trách).
     * Chưa lập hóa đơn → 404.
     *
     * GET /api/encounters/{id}/invoice
     */
    @GetMapping("/encounters/{id}/invoice")
    public ResponseEntity<Invoice> getInvoiceByEncounter(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                invoiceService.getByEncounter(id)
        );
    }

    /**
     * Lấy hóa đơn của bệnh nhân đang đăng nhập.
     *
     * GET /api/invoices/me
     *
     * Không nhận patientId từ request.
     */
    @GetMapping("/invoices/me")
    public ResponseEntity<List<Invoice>> getMyInvoices() {

        return ResponseEntity.ok(
                invoiceService.getMyInvoices()
        );
    }

    /**
     * Xem chi tiết hóa đơn.
     *
     * GET /api/invoices/{id}
     */
    @GetMapping("/invoices/{id}")
    public ResponseEntity<Invoice> getInvoice(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                invoiceService.getInvoiceById(id)
        );
    }

    /**
     * Xem các dòng của hóa đơn.
     *
     * GET /api/invoices/{id}/items
     */
    @GetMapping("/invoices/{id}/items")
    public ResponseEntity<List<InvoiceItem>> getInvoiceItems(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                invoiceService.getInvoiceItems(id)
        );
    }

    /**
     * Admin xem toàn bộ hóa đơn.
     *
     * GET /api/admin/invoices
     */
    @GetMapping("/admin/invoices")
    public ResponseEntity<List<Invoice>> getAllInvoices() {

        return ResponseEntity.ok(
                invoiceService.getAllInvoices()
        );
    }

    /**
     * Đánh dấu hóa đơn đã thanh toán.
     *
     * PUT /api/invoices/{id}/pay
     */
    @PutMapping("/invoices/{id}/pay")
    public ResponseEntity<Invoice> markAsPaid(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                invoiceService.markAsPaid(id)
        );
    }

    /**
     * Hủy hóa đơn.
     *
     * PUT /api/invoices/{id}/void
     */
    @PutMapping("/invoices/{id}/void")
    public ResponseEntity<Invoice> voidInvoice(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                invoiceService.voidInvoice(id)
        );
    }
}
