package com.phenikaa.cse702051.medbook.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.invoice.RevenueReportDTO;
import com.phenikaa.cse702051.medbook.dto.invoice.ServiceRevenueReportDTO;
import com.phenikaa.cse702051.medbook.dto.report.CsvExport;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.repository.InvoiceItemRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.CsvWriter;
import com.phenikaa.cse702051.medbook.util.DateRanges;
import com.phenikaa.cse702051.medbook.util.DateRanges.DateRange;

/**
 * Báo cáo doanh thu và báo cáo dịch vụ khám cho Admin (YCCN-22). Mọi chỉ số tính trên hóa đơn LẬP trong kỳ (theo
 * thời điểm lập), tổng hợp bằng {@code group by} ở CSDL. Giá trị lập hóa đơn luôn bằng đã thu cộng chưa thu, cho
 * tổng chung và cho từng nhóm, vì nó được cộng từ chính hai phần đó; hóa đơn đã hủy chỉ được đếm, không cộng tiền.
 * Cả hai báo cáo đều xuất được ra CSV; mỗi lần xuất ghi một bản audit chỉ gồm bộ lọc và số dòng.
 */
@Service
public class InvoiceReportService {

    public static final String GROUP_NONE = "NONE";
    public static final String GROUP_DAY = "DAY";
    public static final String GROUP_MONTH = "MONTH";

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public InvoiceReportService(
            InvoiceRepository invoiceRepository,
            InvoiceItemRepository invoiceItemRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    /**
     * @param from    ngày lập đầu tiên được tính; bỏ trống cả hai đầu → 30 ngày gần nhất
     * @param to      ngày lập cuối cùng được tính (trọn ngày); khoảng tối đa 366 ngày
     * @param groupBy {@code NONE} (mặc định), {@code DAY} hoặc {@code MONTH}; giá trị khác → 400
     */
    @Transactional(readOnly = true)
    public RevenueReportDTO revenue(LocalDate from, LocalDate to, String groupBy) {
        currentUserService.requireRole("ADMIN");
        String grouping = parseGroupBy(groupBy);
        DateRange range = DateRanges.resolve(from, to, DEFAULT_DAYS, MAX_DAYS);
        LocalDateTime start = range.from().atStartOfDay();
        LocalDateTime endExclusive = range.to().plusDays(1).atStartOfDay();

        Totals overall = new Totals();
        Map<String, GroupTotals> groups = new LinkedHashMap<>();
        switch (grouping) {
            case GROUP_DAY -> {
                for (Object[] row : invoiceRepository.sumByDayAndStatus(start, endExclusive)) {
                    LocalDate day = LocalDate.of(number(row[0]), number(row[1]), number(row[2]));
                    groups.computeIfAbsent(day.toString(), key -> new GroupTotals(DAY_LABEL.format(day)))
                            .totals.add((String) row[3], ((Number) row[4]).longValue(), amount(row[5]));
                }
            }
            case GROUP_MONTH -> {
                for (Object[] row : invoiceRepository.sumByMonthAndStatus(start, endExclusive)) {
                    int year = number(row[0]);
                    int month = number(row[1]);
                    String key = "%04d-%02d".formatted(year, month);
                    groups.computeIfAbsent(key, ignored -> new GroupTotals("%02d/%04d".formatted(month, year)))
                            .totals.add((String) row[2], ((Number) row[3]).longValue(), amount(row[4]));
                }
            }
            default -> {
                for (Object[] row : invoiceRepository.sumByStatus(start, endExclusive)) {
                    overall.add((String) row[0], ((Number) row[1]).longValue(), amount(row[2]));
                }
            }
        }

        List<RevenueReportDTO.Group> groupDtos = new ArrayList<>();
        groups.forEach((key, group) -> {
            overall.add(group.totals);
            groupDtos.add(new RevenueReportDTO.Group(key, group.label, group.totals.invoiced(),
                    group.totals.collected, group.totals.unpaid, group.totals.invoiceCount(),
                    group.totals.voidCount));
        });
        return new RevenueReportDTO(range.from(), range.to(), grouping, overall.invoiced(), overall.collected,
                overall.unpaid, overall.invoiceCount(), overall.voidCount, groupDtos);
    }

    /**
     * Xuất báo cáo doanh thu ra CSV: một dòng cho mỗi ngày hoặc tháng (mặc định theo ngày), kèm dòng tổng cộng.
     * {@code groupBy=NONE} chỉ có dòng tổng cộng.
     */
    @Transactional(readOnly = true)
    public CsvExport exportRevenueCsv(LocalDate from, LocalDate to, String groupBy) {
        RevenueReportDTO report = revenue(from, to, groupBy == null || groupBy.isBlank() ? GROUP_DAY : groupBy);
        CsvWriter csv = new CsvWriter().row(GROUP_MONTH.equals(report.groupBy()) ? "Tháng" : "Ngày",
                "Giá trị lập hóa đơn", "Đã thu", "Chưa thu", "Số hóa đơn", "Số hóa đơn đã hủy");
        for (RevenueReportDTO.Group group : report.groups()) {
            csv.row(group.label(), group.invoicedAmount(), group.collectedAmount(), group.unpaidAmount(),
                    group.invoiceCount(), group.voidCount());
        }
        csv.row("Tổng cộng", report.invoicedAmount(), report.collectedAmount(), report.unpaidAmount(),
                report.invoiceCount(), report.voidCount());
        auditExport("revenue", report.from(), report.to(), report.groups().size());
        return new CsvExport("medbook-doanh-thu-" + report.from() + "_" + report.to() + ".csv", csv.toBytes());
    }

    /**
     * Báo cáo dịch vụ khám: số lần dùng và giá trị của từng dịch vụ trên các hóa đơn không bị hủy lập trong kỳ,
     * giá trị lớn nhất trước. Khoảng ngày mặc định và giới hạn giống báo cáo doanh thu.
     */
    @Transactional(readOnly = true)
    public ServiceRevenueReportDTO services(LocalDate from, LocalDate to) {
        currentUserService.requireRole("ADMIN");
        DateRange range = DateRanges.resolve(from, to, DEFAULT_DAYS, MAX_DAYS);
        List<Object[]> rows = invoiceItemRepository.sumByService(range.from().atStartOfDay(),
                range.to().plusDays(1).atStartOfDay(), InvoiceService.VOID);

        BigDecimal totalQuantity = BigDecimal.ZERO.setScale(2);
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);
        for (Object[] row : rows) {
            totalQuantity = totalQuantity.add(amount(row[4]));
            totalAmount = totalAmount.add(amount(row[5]));
        }
        List<ServiceRevenueReportDTO.Row> services = new ArrayList<>();
        for (Object[] row : rows) {
            BigDecimal lineAmount = amount(row[5]);
            BigDecimal share = totalAmount.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                    : lineAmount.multiply(BigDecimal.valueOf(100)).divide(totalAmount, 2, RoundingMode.HALF_UP);
            services.add(new ServiceRevenueReportDTO.Row((Long) row[0], (String) row[1],
                    row[2] == null ? "Dịch vụ ngoài danh mục" : (String) row[2], ((Number) row[3]).longValue(),
                    amount(row[4]), lineAmount, share));
        }
        return new ServiceRevenueReportDTO(range.from(), range.to(), totalQuantity, totalAmount, services);
    }

    /** Xuất báo cáo dịch vụ khám ra CSV: một dòng cho mỗi dịch vụ, kèm dòng tổng cộng. */
    @Transactional(readOnly = true)
    public CsvExport exportServicesCsv(LocalDate from, LocalDate to) {
        ServiceRevenueReportDTO report = services(from, to);
        CsvWriter csv = new CsvWriter().row("Mã dịch vụ", "Tên dịch vụ", "Số hóa đơn", "Số lượng", "Thành tiền",
                "Tỷ trọng (%)");
        for (ServiceRevenueReportDTO.Row row : report.services()) {
            csv.row(row.serviceCode(), row.serviceName(), row.invoiceCount(), row.quantity(), row.amount(),
                    row.sharePercent());
        }
        csv.row(null, "Tổng cộng", null, report.totalQuantity(), report.totalAmount(), null);
        auditExport("services", report.from(), report.to(), report.services().size());
        return new CsvExport("medbook-dich-vu-" + report.from() + "_" + report.to() + ".csv", csv.toBytes());
    }

    private void auditExport(String report, LocalDate from, LocalDate to, int rowCount) {
        auditLogService.record(AuditEvent.of(AuditActions.REPORT_EXPORT, AuditActions.ENTITY_INVOICES, null)
                .with("report", report)
                .with("from", from.toString())
                .with("to", to.toString())
                .with("rowCount", rowCount));
    }

    private static String parseGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return GROUP_NONE;
        }
        String normalized = groupBy.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case GROUP_NONE, GROUP_DAY, GROUP_MONTH -> normalized;
            default -> throw new FieldValidationException("groupBy", "Chỉ nhóm được theo NONE, DAY hoặc MONTH");
        };
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static BigDecimal amount(Object value) {
        BigDecimal amount = value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** Tổng của một nhóm, cộng dồn theo trạng thái hóa đơn. */
    private static final class Totals {
        private BigDecimal collected = BigDecimal.ZERO.setScale(2);
        private BigDecimal unpaid = BigDecimal.ZERO.setScale(2);
        private long paidCount;
        private long unpaidCount;
        private long voidCount;

        void add(String status, long count, BigDecimal amount) {
            if (InvoiceService.PAID.equals(status)) {
                paidCount += count;
                collected = collected.add(amount);
            } else if (InvoiceService.UNPAID.equals(status)) {
                unpaidCount += count;
                unpaid = unpaid.add(amount);
            } else if (InvoiceService.VOID.equals(status)) {
                voidCount += count;
            }
        }

        void add(Totals other) {
            paidCount += other.paidCount;
            unpaidCount += other.unpaidCount;
            voidCount += other.voidCount;
            collected = collected.add(other.collected);
            unpaid = unpaid.add(other.unpaid);
        }

        /** Giá trị lập hóa đơn = đã thu + chưa thu (hóa đơn đã hủy không tính). */
        BigDecimal invoiced() {
            return collected.add(unpaid);
        }

        long invoiceCount() {
            return paidCount + unpaidCount;
        }
    }

    private static final class GroupTotals {
        private final String label;
        private final Totals totals = new Totals();

        GroupTotals(String label) {
            this.label = label;
        }
    }
}
