package com.phenikaa.cse702051.medbook.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bệnh nhân gửi hoặc sửa đánh giá cho một lần khám đã hoàn thành. */
public record ReviewRequest(
        @NotNull(message = "Phải chọn số sao")
        @Min(value = 1, message = "Số sao từ 1 đến 5")
        @Max(value = 5, message = "Số sao từ 1 đến 5")
        Integer rating,

        @Size(max = 1000, message = "Nhận xét tối đa 1000 ký tự")
        String comment
) {
}
