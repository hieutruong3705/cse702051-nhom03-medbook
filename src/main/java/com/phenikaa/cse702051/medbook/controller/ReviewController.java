package com.phenikaa.cse702051.medbook.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.review.ReviewDTO;
import com.phenikaa.cse702051.medbook.dto.review.ReviewPublicDTO;
import com.phenikaa.cse702051.medbook.dto.review.ReviewRequest;
import com.phenikaa.cse702051.medbook.service.ReviewService;

import jakarta.validation.Valid;

/**
 * Đánh giá lần khám (YCCN-38 đến 40). {@code GET /reviews} công khai; gửi, sửa, xóa và xem đánh giá của mình chỉ
 * dành cho bệnh nhân (xem {@code SecurityConfig}), service kiểm thêm quyền sở hữu trên từng bản ghi.
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** Đánh giá công khai. {@code sort}: {@code createdAt} (mặc định, mới nhất trước) hoặc {@code rating}. */
    @GetMapping("/reviews")
    public PageResponse<ReviewPublicDTO> listPublic(
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        return reviewService.listPublic(doctorId, specialtyId, rating, page, size, sort);
    }

    @GetMapping("/reviews/me")
    public PageResponse<ReviewDTO> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reviewService.listMine(page, size);
    }

    @PostMapping("/appointments/{appointmentId}/review")
    public ResponseEntity<ReviewDTO> create(@PathVariable Long appointmentId,
            @Valid @RequestBody ReviewRequest request) {
        ReviewDTO created = reviewService.create(appointmentId, request);
        return ResponseEntity.created(URI.create("/api/v1/reviews/" + created.id())).body(created);
    }

    @GetMapping("/appointments/{appointmentId}/review")
    public ReviewDTO getForAppointment(@PathVariable Long appointmentId) {
        return reviewService.getMineForAppointment(appointmentId);
    }

    @PutMapping("/reviews/{id}")
    public ReviewDTO update(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return reviewService.update(id, request);
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reviewService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
