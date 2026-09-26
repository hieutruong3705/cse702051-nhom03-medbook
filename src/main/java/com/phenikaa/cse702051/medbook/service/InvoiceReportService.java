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
     * Thá»‘ng kĂª doanh thu tá»« táº¥t cáº£ hĂ³a Ä‘Æ¡n.
     *
     * PAID vĂ  UNPAID Ä‘Æ°á»£c tĂ­nh vĂ o tá»•ng giĂ¡ trá»‹ hĂ³a Ä‘Æ¡n.
     * VOID chá»‰ Ä‘Æ°á»£c thá»‘ng kĂª sá»‘ lÆ°á»£ng, khĂ´ng tĂ­nh vĂ o doanh thu.
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
     * Thá»‘ng kĂª doanh thu trong khoáº£ng thá»i gian.
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
                    "Thá»i gian báº¯t Ä‘áº§u khĂ´ng Ä‘Æ°á»£c lá»›n hÆ¡n " +
                    "thá»i gian káº¿t thĂºc"
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
     * XĂ¢y dá»±ng bĂ¡o cĂ¡o doanh thu.
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
             * Lá»c tá»« thá»i gian báº¯t Ä‘áº§u.
             */
            if (from != null) {

                if (issuedAt == null
                        || issuedAt.isBefore(from)) {

                    continue;
                }
            }

            /*
             * Lá»c Ä‘áº¿n thá»i gian káº¿t thĂºc.
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
             * HĂ³a Ä‘Æ¡n PAID:
             * - TÄƒng sá»‘ lÆ°á»£ng paid.
             * - TĂ­nh vĂ o paidAmount.
             * - TĂ­nh vĂ o totalAmount.
             */
            if ("PAID".equalsIgnoreCase(status)) {

                paidInvoices++;

                paidAmount =
                        paidAmount.add(amount);

                totalAmount =
                        totalAmount.add(amount);

            /*
             * HĂ³a Ä‘Æ¡n UNPAID:
             * - TÄƒng sá»‘ lÆ°á»£ng unpaid.
             * - TĂ­nh vĂ o unpaidAmount.
             * - TĂ­nh vĂ o totalAmount.
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
             * HĂ³a Ä‘Æ¡n VOID:
             * - Chá»‰ Ä‘áº¿m sá»‘ lÆ°á»£ng.
             * - KhĂ´ng tĂ­nh vĂ o doanh thu.
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
