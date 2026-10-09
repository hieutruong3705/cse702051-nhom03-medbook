package com.phenikaa.cse702051.medbook.controller;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.service.InvoiceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Invoice> createInvoice(
            @RequestParam Long patientId,
            @RequestParam Long appointmentId,
            @RequestParam BigDecimal subtotal,
            @RequestParam(required = false) BigDecimal discountAmount) {

        Invoice invoice = invoiceService.createInvoice(
                patientId,
                appointmentId,
                subtotal,
                discountAmount
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
    }
}
