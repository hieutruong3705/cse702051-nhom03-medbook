package com.phenikaa.cse702051.medbook.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.invoice.InvoiceDTO;
import com.phenikaa.cse702051.medbook.dto.invoice.InvoiceItemDTO;
import com.phenikaa.cse702051.medbook.dto.invoice.InvoiceLineRequest;
import com.phenikaa.cse702051.medbook.dto.invoice.InvoiceSummaryDTO;
import com.phenikaa.cse702051.medbook.service.InvoiceService;

/**
 * Hóa đơn của lần khám (YCCN-22) cho bác sĩ phụ trách và bệnh nhân. Lần khám và bệnh nhân luôn lấy từ đường dẫn
 * hoặc JWT; đơn giá không bao giờ nhận từ client. Thu và hủy nằm ở {@link AdminInvoiceController}.
 */
@RestController
@RequestMapping("/api/v1")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /** Bác sĩ phụ trách lập hóa đơn: body là mảng dòng {@code {serviceId, quantity}}. Đã có hóa đơn → 409. */
    @PostMapping("/encounters/{encounterId}/invoice")
    public InvoiceDTO create(
            @PathVariable Long encounterId,
            @RequestParam(required = false, defaultValue = "0") BigDecimal discountAmount,
            @RequestBody List<InvoiceLineRequest> lines) {
        return invoiceService.create(encounterId, lines, discountAmount);
    }

    /** Hóa đơn của một lần khám (bệnh nhân chủ hoặc bác sĩ phụ trách). Chưa lập → 404. */
    @GetMapping("/encounters/{encounterId}/invoice")
    public InvoiceDTO getByEncounter(@PathVariable Long encounterId) {
        return invoiceService.getByEncounter(encounterId);
    }

    /** Hóa đơn của bệnh nhân đang đăng nhập, mới nhất trước. */
    @GetMapping("/invoices/me")
    public PageResponse<InvoiceSummaryDTO> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return invoiceService.listMine(status, from, to, page, size);
    }

    @GetMapping("/invoices/{id:\\d+}")
    public InvoiceDTO get(@PathVariable Long id) {
        return invoiceService.get(id);
    }

    @GetMapping("/invoices/{id:\\d+}/items")
    public List<InvoiceItemDTO> items(@PathVariable Long id) {
        return invoiceService.listItems(id);
    }
}
