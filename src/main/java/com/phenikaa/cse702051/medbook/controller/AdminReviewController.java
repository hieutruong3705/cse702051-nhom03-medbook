package com.phenikaa.cse702051.medbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.review.HideReviewRequest;
import com.phenikaa.cse702051.medbook.dto.review.ReviewAdminDTO;
import com.phenikaa.cse702051.medbook.service.ReviewService;

import jakarta.validation.Valid;

/** Kiểm duyệt đánh giá (YCCN-41). Chỉ ADMIN (xem {@code SecurityConfig}: {@code /admin/**}). */
@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;

    public AdminReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** {@code status}: {@code VISIBLE} hoặc {@code HIDDEN}; để trống là mọi trạng thái. */
    @GetMapping
    public PageResponse<ReviewAdminDTO> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewService.listForAdmin(status, doctorId, rating, page, size);
    }

    @PatchMapping("/{id}/hide")
    public ReviewAdminDTO hide(@PathVariable Long id, @Valid @RequestBody HideReviewRequest request) {
        return reviewService.hide(id, request);
    }

    @PatchMapping("/{id}/show")
    public ReviewAdminDTO show(@PathVariable Long id) {
        return reviewService.show(id);
    }
}
