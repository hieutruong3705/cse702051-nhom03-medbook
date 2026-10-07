package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.report.AdminAppointmentDTO;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentReportRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.DateRanges;
import com.phenikaa.cse702051.medbook.util.PageRequests;

/**
 * Admin theo dõi lịch hẹn của toàn phòng khám ở mức hành chính (YCCN-22): ai khám với ai, lúc nào, trạng thái
 * gì. Không có ghi chú, lý do hủy hay nội dung khám; các dữ liệu đó chỉ dành cho bệnh nhân và bác sĩ phụ trách.
 */
@Service
public class AdminAppointmentService {

    /** Dùng khi Admin không giới hạn một đầu của khoảng ngày khám. */
    private static final LocalDate EARLIEST = LocalDate.of(1970, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    private final AppointmentReportRepository reportRepository;
    private final CurrentUserService currentUserService;

    public AdminAppointmentService(
            AppointmentReportRepository reportRepository,
            CurrentUserService currentUserService) {
        this.reportRepository = reportRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Danh sách lịch hẹn theo ngày khám, mới nhất trước. Mọi bộ lọc đều tùy chọn; {@code from} sau {@code to} → 400.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminAppointmentDTO> list(LocalDate from, LocalDate to, Long doctorId, Long specialtyId,
            AppointmentStatus status, int page, int size) {
        currentUserService.requireRole("ADMIN");
        DateRanges.requireOrdered(from, to);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PageRequests.clampSize(size));
        return PageResponse.from(reportRepository.search(
                from != null ? from : EARLIEST,
                to != null ? to : LATEST,
                doctorId, specialtyId, status, pageable));
    }
}
