package com.phenikaa.cse702051.medbook.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.reception.ReceptionOverviewDTO;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentReportRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Số liệu trong ngày cho quầy lễ tân. Ba truy vấn tổng hợp chạy ở CSDL ({@code count}, {@code sum},
 * {@code group by}); không tải danh sách lên bộ nhớ để đếm.
 */
@Service
public class ReceptionOverviewService {

    private final AppointmentReportRepository appointmentReportRepository;
    private final InvoiceRepository invoiceRepository;
    private final CurrentUserService currentUserService;

    public ReceptionOverviewService(
            AppointmentReportRepository appointmentReportRepository,
            InvoiceRepository invoiceRepository,
            CurrentUserService currentUserService) {
        this.appointmentReportRepository = appointmentReportRepository;
        this.invoiceRepository = invoiceRepository;
        this.currentUserService = currentUserService;
    }

    /** {@code date} để trống là hôm nay. */
    @Transactional(readOnly = true)
    public ReceptionOverviewDTO overview(LocalDate date) {
        currentUserService.requireRole("RECEPTIONIST");
        LocalDate day = date != null ? date : LocalDate.now();

        Map<AppointmentStatus, Long> byStatus = new EnumMap<>(AppointmentStatus.class);
        for (Object[] row : appointmentReportRepository.countByStatus(day, day, null, null)) {
            byStatus.put((AppointmentStatus) row[0], ((Number) row[1]).longValue());
        }
        Object[] unpaid = single(invoiceRepository.sumUnpaid());
        Object[] collected = single(invoiceRepository.sumCollected(day.atStartOfDay(), day.plusDays(1).atStartOfDay()));

        return new ReceptionOverviewDTO(
                day,
                byStatus.getOrDefault(AppointmentStatus.BOOKED, 0L),
                byStatus.getOrDefault(AppointmentStatus.IN_PROGRESS, 0L),
                byStatus.getOrDefault(AppointmentStatus.COMPLETED, 0L),
                byStatus.getOrDefault(AppointmentStatus.CANCELLED, 0L),
                ((Number) unpaid[0]).longValue(),
                money(unpaid[1]),
                ((Number) collected[0]).longValue(),
                money(collected[1]));
    }

    private static Object[] single(List<Object[]> rows) {
        return rows.isEmpty() ? new Object[] { 0L, BigDecimal.ZERO } : rows.get(0);
    }

    /** Tổng tiền luôn có hai chữ số thập phân; chưa có hóa đơn nào thì là 0.00, không phải null. */
    private static BigDecimal money(Object value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO
                : value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
