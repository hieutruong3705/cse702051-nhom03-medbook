package com.phenikaa.cse702051.medbook.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.invoice.AdminInvoiceDTO;
import com.phenikaa.cse702051.medbook.dto.invoice.CollectInvoiceRequest;
import com.phenikaa.cse702051.medbook.dto.invoice.VoidInvoiceRequest;
import com.phenikaa.cse702051.medbook.service.InvoiceService;

import jakarta.validation.Valid;

/**
 * Admin theo dõi hóa đơn, ghi nhận đã thu và hủy (YCCN-22). Chỉ ADMIN (xem {@code SecurityConfig}:
 * {@code /admin/**}). Thu và hủy chỉ áp dụng cho hóa đơn chưa thu; trạng thái khác → 409.
 */
@RestController
@RequestMapping("/api/v1/admin/invoices")
public class AdminInvoiceController {

    private final InvoiceService invoiceService;

    public AdminInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /** Lọc theo trạng thái, ngày lập và từ khóa (mã hóa đơn, tên hoặc mã bệnh nhân); mới nhất trước. */
    @GetMapping
    public PageResponse<AdminInvoiceDTO> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return invoiceService.listForAdmin(status, from, to, keyword, page, size);
    }

    /** Ghi nhận đã thu tiền tại quầy. Body không bắt buộc. */
    @PatchMapping("/{id}/collect")
    public AdminInvoiceDTO collect(@PathVariable Long id,
            @Valid @RequestBody(required = false) CollectInvoiceRequest request) {
        return invoiceService.collect(id);
    }

    /** Hủy hóa đơn chưa thu; bắt buộc có lý do. */
    @PatchMapping("/{id}/void")
    public AdminInvoiceDTO voidInvoice(@PathVariable Long id, @Valid @RequestBody VoidInvoiceRequest request) {
        return invoiceService.voidInvoice(id, request.reason());
    }
}
