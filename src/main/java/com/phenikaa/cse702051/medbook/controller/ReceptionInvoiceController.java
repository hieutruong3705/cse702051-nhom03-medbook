package com.phenikaa.cse702051.medbook.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionInvoiceDTO;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionInvoiceItemDTO;
import com.phenikaa.cse702051.medbook.service.InvoiceService;

/**
 * Hóa đơn tại quầy lễ tân: tìm, xem các dòng, ghi nhận đã thu. Chỉ RECEPTIONIST (xem {@code SecurityConfig}:
 * {@code /reception/**}). Cố ý không có đường hủy hóa đơn: việc đó chỉ dành cho Quản trị viên.
 */
@RestController
@RequestMapping("/api/v1/reception/invoices")
public class ReceptionInvoiceController {

    private final InvoiceService invoiceService;

    public ReceptionInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /** Lọc theo trạng thái, ngày lập và từ khóa (mã hóa đơn, tên hoặc mã bệnh nhân); mới nhất trước. */
    @GetMapping
    public PageResponse<ReceptionInvoiceDTO> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return invoiceService.listForReception(status, from, to, keyword, page, size);
    }

    @GetMapping("/{id:\\d+}/items")
    public List<ReceptionInvoiceItemDTO> items(@PathVariable Long id) {
        return invoiceService.listItemsForReception(id);
    }

    /** Ghi nhận đã thu tiền tại quầy. Không có body. */
    @PatchMapping("/{id:\\d+}/collect")
    public ReceptionInvoiceDTO collect(@PathVariable Long id) {
        return invoiceService.collectAtDesk(id);
    }
}
