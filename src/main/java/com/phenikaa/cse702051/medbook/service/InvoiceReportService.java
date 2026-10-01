package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceReportService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceReportService(
            InvoiceRepository invoiceRepository
    ) {
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Thống kê doanh thu từ tất cả hóa đơn.
     *
     * PAID và UNPAID được tính vào tổng giá trị hóa đơn.
     * VOID chỉ được thống kê số lượng, không tính vào doanh thu.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRevenueReport() {

        List<Invoice> invoices =
                invoiceRepository.findAll();

        return buildReport(
                invoices,
                null,
                null
        );
    }

    /**
     * Thống kê doanh thu trong khoảng thời gian.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRevenueReport(
            LocalDateTime from,
            LocalDateTime to
    ) {

        if (from != null
                && to != null
                && from.isAfter(to)) {

            throw new IllegalArgumentException(
                    "Thời gian bắt đầu không được lớn hơn " +
                    "thời gian kết thúc"
            );
        }

        List<Invoice> invoices =
                invoiceRepository.findAll();

        return buildReport(
                invoices,
                from,
                to
        );
    }

    /**
     * Xây dựng báo cáo doanh thu.
     */
    private Map<String, Object> buildReport(
            List<Invoice> invoices,
            LocalDateTime from,
            LocalDateTime to
    ) {

        long totalInvoices = 0;
        long paidInvoices = 0;
        long unpaidInvoices = 0;
        long voidInvoices = 0;

        BigDecimal totalAmount =
                BigDecimal.ZERO;

        BigDecimal paidAmount =
                BigDecimal.ZERO;

        BigDecimal unpaidAmount =
                BigDecimal.ZERO;

        for (Invoice invoice : invoices) {

            if (invoice == null) {
                continue;
            }

            LocalDateTime issuedAt =
                    invoice.getIssuedAt();

            /*
             * Lọc từ thời gian bắt đầu.
             */
            if (from != null) {

                if (issuedAt == null
                        || issuedAt.isBefore(from)) {

                    continue;
                }
            }

            /*
             * Lọc đến thời gian kết thúc.
             */
            if (to != null) {

                if (issuedAt == null
                        || issuedAt.isAfter(to)) {

                    continue;
                }
            }

            totalInvoices++;

            BigDecimal amount =
                    invoice.getTotalAmount();

            if (amount == null) {
                amount = BigDecimal.ZERO;
            }

            String status =
                    invoice.getStatus();

            /*
             * Hóa đơn PAID:
             * - Tăng số lượng paid.
             * - Tính vào paidAmount.
             * - Tính vào totalAmount.
             */
            if ("PAID".equalsIgnoreCase(status)) {

                paidInvoices++;

                paidAmount =
                        paidAmount.add(amount);

                totalAmount =
                        totalAmount.add(amount);

            /*
             * Hóa đơn UNPAID:
             * - Tăng số lượng unpaid.
             * - Tính vào unpaidAmount.
             * - Tính vào totalAmount.
             */
            } else if (
                    "UNPAID".equalsIgnoreCase(status)
            ) {

                unpaidInvoices++;

                unpaidAmount =
                        unpaidAmount.add(amount);

                totalAmount =
                        totalAmount.add(amount);

            /*
             * Hóa đơn VOID:
             * - Chỉ đếm số lượng.
             * - Không tính vào doanh thu.
             */
            } else if (
                    "VOID".equalsIgnoreCase(status)
            ) {

                voidInvoices++;
            }
        }

        Map<String, Object> report =
                new HashMap<>();

        report.put(
                "from",
                from
        );

        report.put(
                "to",
                to
        );

        report.put(
                "totalInvoices",
                totalInvoices
        );

        report.put(
                "paidInvoices",
                paidInvoices
        );

        report.put(
                "unpaidInvoices",
                unpaidInvoices
        );

        report.put(
                "voidInvoices",
                voidInvoices
        );

        report.put(
                "totalAmount",
                totalAmount
        );

        report.put(
                "paidAmount",
                paidAmount
        );

        report.put(
                "unpaidAmount",
                unpaidAmount
        );

        return report;
    }
}
