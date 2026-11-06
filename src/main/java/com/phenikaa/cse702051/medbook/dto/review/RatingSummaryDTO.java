package com.phenikaa.cse702051.medbook.dto.review;

import java.util.List;

/**
 * Tóm tắt điểm đánh giá của một bác sĩ, chỉ tính đánh giá đang hiện. {@code averageRating} làm tròn một chữ số
 * thập phân, {@code null} khi chưa có đánh giá; {@code distribution} là số đánh giá của 1, 2, 3, 4, 5 sao.
 */
public record RatingSummaryDTO(
        Long doctorId,
        Double averageRating,
        long reviewCount,
        List<Long> distribution
) {
}
