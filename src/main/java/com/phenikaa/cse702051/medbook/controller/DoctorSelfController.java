package com.phenikaa.cse702051.medbook.controller;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.doctor.DoctorOwnProfileDTO;
import com.phenikaa.cse702051.medbook.dto.doctor.DoctorProfileUpdateRequest;
import com.phenikaa.cse702051.medbook.dto.review.RatingSummaryDTO;
import com.phenikaa.cse702051.medbook.dto.review.ReviewPublicDTO;
import com.phenikaa.cse702051.medbook.service.DoctorService;
import com.phenikaa.cse702051.medbook.service.ReviewService;

import jakarta.validation.Valid;

/**
 * Bác sĩ đang đăng nhập tự quản lý hồ sơ giới thiệu và xem đánh giá về mình (YCCN-43, 44). Chỉ DOCTOR (xem
 * {@code SecurityConfig}: luật {@code /doctors/me/**} đứng trước luật công khai {@code /doctors/**}).
 */
@RestController
@RequestMapping("/api/v1/doctors/me")
public class DoctorSelfController {

    private final DoctorService doctorService;
    private final ReviewService reviewService;

    public DoctorSelfController(DoctorService doctorService, ReviewService reviewService) {
        this.doctorService = doctorService;
        this.reviewService = reviewService;
    }

    @GetMapping("/profile")
    public DoctorOwnProfileDTO getProfile() {
        return doctorService.getOwnProfile();
    }

    @PutMapping("/profile")
    public DoctorOwnProfileDTO updateProfile(@Valid @RequestBody DoctorProfileUpdateRequest request) {
        return doctorService.updateOwnProfile(request);
    }

    @GetMapping("/reviews")
    public PageResponse<ReviewPublicDTO> reviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reviewService.listForCurrentDoctor(page, size);
    }

    @GetMapping("/rating-summary")
    public RatingSummaryDTO ratingSummary() {
        return reviewService.summaryForCurrentDoctor();
    }

    /** Bác sĩ tự tải ảnh đại diện (JPEG, PNG, WebP, tối đa 1 MB). */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DoctorOwnProfileDTO uploadAvatar(@RequestParam("file") MultipartFile file) {
        return doctorService.updateOwnAvatar(file);
    }

    /** Bác sĩ gỡ ảnh đại diện. */
    @DeleteMapping("/avatar")
    public DoctorOwnProfileDTO removeAvatar() {
        return doctorService.updateOwnAvatar(null);
    }
}
