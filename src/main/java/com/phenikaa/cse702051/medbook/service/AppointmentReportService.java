package com.phenikaa.cse702051.medbook.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.report.AdminAppointmentDTO;
import com.phenikaa.cse702051.medbook.dto.report.AppointmentReportDTO;
import com.phenikaa.cse702051.medbook.dto.report.CsvExport;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.UnprocessableEntityException;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentReportRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.CsvWriter;
import com.phenikaa.cse702051.medbook.util.DateRanges;
import com.phenikaa.cse702051.medbook.util.DateRanges.DateRange;

/**
 * Báo cáo lịch khám cho Admin (YCCN-22): số lịch theo trạng thái và tỷ lệ hủy, gộp theo ngày, tháng, năm, bác sĩ hoặc
 * chuyên khoa; xuất CSV. Mốc thời gian là ngày khám. Tổng hợp chạy ở CSDL: mỗi báo cáo dùng đúng một truy vấn
 * {@code group by}, tổng chung được cộng từ chính các nhóm nên luôn khớp.
 */
@Service
public class AppointmentReportService {

    public static final String GROUP_NONE = "NONE";
    public static final String GROUP_DAY = "DAY";
    public static final String GROUP_MONTH = "MONTH";
    public static final String GROUP_YEAR = "YEAR";
    public static final String GROUP_DOCTOR = "DOCTOR";
    public static final String GROUP_SPECIALTY = "SPECIALTY";

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AppointmentReportRepository reportRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final long exportMaxRows;

    public AppointmentReportService(
            AppointmentReportRepository reportRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            @Value("${medbook.reports.export-max-rows:50000}") long exportMaxRows) {
        this.reportRepository = reportRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.exportMaxRows = exportMaxRows;
    }

    /**
     * @param groupBy {@code NONE} (mặc định), {@code DAY}, {@code MONTH}, {@code YEAR}, {@code DOCTOR} hoặc
     *                {@code SPECIALTY}; giá trị khác → 400
     */
    @Transactional(readOnly = true)
    public AppointmentReportDTO report(LocalDate from, LocalDate to, Long doctorId, Long specialtyId,
            String groupBy) {
        currentUserService.requireRole("ADMIN");
        String grouping = parseGroupBy(groupBy);
        DateRange range = DateRanges.resolve(from, to, DEFAULT_DAYS, MAX_DAYS);

        Counts overall = new Counts();
        Map<String, GroupCounts> groups = new LinkedHashMap<>();
        switch (grouping) {
            case GROUP_DAY -> {
                for (Object[] row : reportRepository.countByDayAndStatus(range.from(), range.to(), doctorId,
                        specialtyId)) {
                    LocalDate day = (LocalDate) row[0];
                    groups.computeIfAbsent(day.toString(), key -> new GroupCounts(DAY_LABEL.format(day)))
                            .counts.add((AppointmentStatus) row[1], (Long) row[2]);
                }
            }
            case GROUP_MONTH -> {
                for (Object[] row : reportRepository.countByMonthAndStatus(range.from(), range.to(), doctorId,
                        specialtyId)) {
                    int year = ((Number) row[0]).intValue();
                    int month = ((Number) row[1]).intValue();
                    groups.computeIfAbsent("%04d-%02d".formatted(year, month),
                            key -> new GroupCounts("%02d/%04d".formatted(month, year)))
                            .counts.add((AppointmentStatus) row[2], (Long) row[3]);
                }
            }
            case GROUP_YEAR -> {
                for (Object[] row : reportRepository.countByYearAndStatus(range.from(), range.to(), doctorId,
                        specialtyId)) {
                    String year = "%04d".formatted(((Number) row[0]).intValue());
                    groups.computeIfAbsent(year, GroupCounts::new)
                            .counts.add((AppointmentStatus) row[1], (Long) row[2]);
                }
            }
            case GROUP_DOCTOR -> {
                for (Object[] row : reportRepository.countByDoctorAndStatus(range.from(), range.to(), doctorId,
                        specialtyId)) {
                    groups.computeIfAbsent(String.valueOf(row[0]), key -> new GroupCounts((String) row[1]))
                            .counts.add((AppointmentStatus) row[2], (Long) row[3]);
                }
            }
            case GROUP_SPECIALTY -> {
                for (Object[] row : reportRepository.countBySpecialtyAndStatus(range.from(), range.to(), doctorId,
                        specialtyId)) {
                    String key = row[0] == null ? "none" : String.valueOf(row[0]);
                    String label = row[1] == null ? "Chưa phân chuyên khoa" : (String) row[1];
                    groups.computeIfAbsent(key, ignored -> new GroupCounts(label))
                            .counts.add((AppointmentStatus) row[2], (Long) row[3]);
                }
            }
            default -> {
                for (Object[] row : reportRepository.countByStatus(range.from(), range.to(), doctorId,
                        specialtyId)) {
                    overall.add((AppointmentStatus) row[0], (Long) row[1]);
                }
            }
        }

        List<AppointmentReportDTO.Group> groupDtos = new ArrayList<>();
        groups.forEach((key, group) -> {
            overall.add(group.counts);
            groupDtos.add(new AppointmentReportDTO.Group(key, group.label, group.counts.total(),
                    group.counts.completed, group.counts.cancelled,
                    rate(group.counts.cancelled, group.counts.total())));
        });
        return new AppointmentReportDTO(range.from(), range.to(), grouping, overall.total(), overall.booked,
                overall.inProgress, overall.completed, overall.cancelled, rate(overall.cancelled, overall.total()),
                groupDtos);
    }

    /**
     * Xuất danh sách lịch hẹn khớp bộ lọc ra CSV (chỉ cột hành chính). Vượt giới hạn số dòng → 422, không tạo tệp.
     * Mỗi lần xuất ghi một bản audit chỉ gồm bộ lọc và số dòng.
     */
    @Transactional(readOnly = true)
    public CsvExport exportCsv(LocalDate from, LocalDate to, Long doctorId, Long specialtyId, String format) {
        currentUserService.requireRole("ADMIN");
        if (format != null && !format.isBlank() && !"csv".equalsIgnoreCase(format.trim())) {
            throw new FieldValidationException("format", "Chỉ hỗ trợ xuất định dạng csv");
        }
        DateRange range = DateRanges.resolve(from, to, DEFAULT_DAYS, MAX_DAYS);

        long matching = reportRepository.countMatching(range.from(), range.to(), doctorId, specialtyId);
        if (matching > exportMaxRows) {
            throw new UnprocessableEntityException("Báo cáo có " + matching + " dòng, vượt giới hạn "
                    + exportMaxRows + " dòng mỗi lần xuất. Vui lòng thu hẹp khoảng ngày!");
        }

        auditLogService.record(AuditEvent.of(AuditActions.REPORT_EXPORT, AuditActions.ENTITY_APPOINTMENTS, null)
                .with("report", "appointments")
                .with("from", range.from().toString())
                .with("to", range.to().toString())
                .with("doctorId", doctorId)
                .with("specialtyId", specialtyId)
                .with("rowCount", matching));

        CsvWriter csv = new CsvWriter().row("Mã lịch", "Bệnh nhân", "Mã bệnh nhân", "Bác sĩ", "Chuyên khoa",
                "Dịch vụ", "Ngày khám", "Giờ bắt đầu", "Giờ kết thúc", "Trạng thái", "Thời điểm đặt");
        for (AdminAppointmentDTO row : reportRepository.findForExport(range.from(), range.to(), doctorId,
                specialtyId)) {
            csv.row(row.id(), row.patientName(), row.patientCode(), row.doctorName(), row.specialtyName(),
                    row.serviceName(), DAY_LABEL.format(row.date()), TIME.format(row.startTime()),
                    TIME.format(row.endTime()), statusLabel(row.status()),
                    row.createdAt() == null ? null : DATE_TIME.format(row.createdAt()));
        }
        return new CsvExport("medbook-lich-kham-" + range.from() + "_" + range.to() + ".csv", csv.toBytes());
    }

    // ================= Chi tiết =================

    private static String parseGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return GROUP_NONE;
        }
        String normalized = groupBy.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case GROUP_NONE, GROUP_DAY, GROUP_MONTH, GROUP_YEAR, GROUP_DOCTOR, GROUP_SPECIALTY -> normalized;
            default -> throw new FieldValidationException("groupBy",
                    "Chỉ nhóm được theo NONE, DAY, MONTH, YEAR, DOCTOR hoặc SPECIALTY");
        };
    }

    /** Tỷ lệ hủy tính theo phần trăm, hai chữ số thập phân; không có lịch nào thì bằng 0. */
    private static BigDecimal rate(long cancelled, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(cancelled).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static String statusLabel(AppointmentStatus status) {
        return switch (status) {
            case BOOKED -> "Đã đặt";
            case IN_PROGRESS -> "Đang khám";
            case COMPLETED -> "Hoàn thành";
            case CANCELLED -> "Đã hủy";
        };
    }

    private static final class Counts {
        private long booked;
        private long inProgress;
        private long completed;
        private long cancelled;

        void add(AppointmentStatus status, long count) {
            switch (status) {
                case BOOKED -> booked += count;
                case IN_PROGRESS -> inProgress += count;
                case COMPLETED -> completed += count;
                case CANCELLED -> cancelled += count;
            }
        }

        void add(Counts other) {
            booked += other.booked;
            inProgress += other.inProgress;
            completed += other.completed;
            cancelled += other.cancelled;
        }

        long total() {
            return booked + inProgress + completed + cancelled;
        }
    }

    private static final class GroupCounts {
        private final String label;
        private final Counts counts = new Counts();

        GroupCounts(String label) {
            this.label = label;
        }
    }
}
