package com.phenikaa.cse702051.medbook.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.AppointmentSlotDTO;
import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.doctor.DoctorPublicDTO;
import com.phenikaa.cse702051.medbook.dto.review.RatingSummaryDTO;
import com.phenikaa.cse702051.medbook.dto.review.ReviewPublicDTO;
import com.phenikaa.cse702051.medbook.service.AppointmentSlotService;
import com.phenikaa.cse702051.medbook.service.DoctorService;
import com.phenikaa.cse702051.medbook.service.ReviewService;

/**
 * Bác sĩ trên trang công khai (YCCN-08, 09, 25): không cần đăng nhập, chỉ bác sĩ đang hoạt động, không lộ trường
 * nội bộ. Quản trị hồ sơ bác sĩ nằm ở {@link AdminDoctorController}.
 */
@RestController
@RequestMapping("/api/v1/doctors")
public class DoctorController {

    private final DoctorService doctorService;
    private final AppointmentSlotService slotService;
    private final ReviewService reviewService;

    public DoctorController(DoctorService doctorService, AppointmentSlotService slotService,
            ReviewService reviewService) {
        this.doctorService = doctorService;
        this.slotService = slotService;
        this.reviewService = reviewService;
    }

    /** Tìm bác sĩ theo tên hoặc tên chuyên khoa. {@code sort}: {@code fullName} (mặc định) hoặc {@code id}. */
    @GetMapping
    public PageResponse<DoctorPublicDTO> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return doctorService.searchActive(keyword, specialtyId, page, size, sort);
    }

    @GetMapping("/{id}")
    public DoctorPublicDTO get(@PathVariable Long id) {
        return doctorService.getActive(id);
    }

    // YCCN 09, 10: Slot còn trống của bác sĩ trong một ngày (công khai, theo hợp đồng API mục 7.3)
    @GetMapping("/{id}/slots")
    public ResponseEntity<List<AppointmentSlotDTO>> getAvailableSlots(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(slotService.getAvailableSlots(id, date));
    }

    /** Đánh giá đang hiện của một bác sĩ, mới nhất trước; {@code sort}: {@code createdAt} hoặc {@code rating}. */
    @GetMapping("/{id}/reviews")
    public PageResponse<ReviewPublicDTO> reviews(
            @PathVariable Long id,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String sort) {
        return reviewService.listPublicForDoctor(id, rating, page, size, sort);
    }

    @GetMapping("/{id}/rating-summary")
    public RatingSummaryDTO ratingSummary(@PathVariable Long id) {
        return reviewService.summaryForDoctor(id);
    }
}
