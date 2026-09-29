package com.phenikaa.cse702051.medbook.controller;

import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.model.InvoiceItem;
import com.phenikaa.cse702051.medbook.service.InvoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(
            InvoiceService invoiceService
    ) {
        this.invoiceService = invoiceService;
    }

    /**
     * Táº¡o hĂ³a Ä‘Æ¡n cho encounter.
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
     * Láº¥y hĂ³a Ä‘Æ¡n cá»§a bá»‡nh nhĂ¢n Ä‘ang Ä‘Äƒng nháº­p.
     *
     * GET /api/invoices/me
     *
     * KhĂ´ng nháº­n patientId tá»« request.
     */
    @GetMapping("/invoices/me")
    public ResponseEntity<List<Invoice>> getMyInvoices() {

        return ResponseEntity.ok(
                invoiceService.getMyInvoices()
        );
    }

    /**
     * Xem chi tiáº¿t hĂ³a Ä‘Æ¡n.
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
     * Xem cĂ¡c dĂ²ng cá»§a hĂ³a Ä‘Æ¡n.
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
     * Admin xem toĂ n bá»™ hĂ³a Ä‘Æ¡n.
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
     * ÄĂ¡nh dáº¥u hĂ³a Ä‘Æ¡n Ä‘Ă£ thanh toĂ¡n.
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
     * Há»§y hĂ³a Ä‘Æ¡n.
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
