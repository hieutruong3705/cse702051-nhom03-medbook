package com.phenikaa.cse702051.medbook.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Quản trị viên ẩn một đánh giá; lý do bắt buộc để giữ vết kiểm duyệt. */
public record HideReviewRequest(
        @NotBlank(message = "Phải nhập lý do ẩn đánh giá")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
