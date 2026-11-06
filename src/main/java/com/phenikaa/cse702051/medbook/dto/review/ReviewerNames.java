package com.phenikaa.cse702051.medbook.dto.review;

import java.util.Locale;

/**
 * Tên người đánh giá trên trang công khai: giữ họ, các phần còn lại chỉ giữ chữ cái đầu
 * ({@code "Nguyễn Văn An"} → {@code "Nguyễn V. A."}), để đánh giá có người thật đứng sau mà không lộ danh tính.
 */
public final class ReviewerNames {

    private ReviewerNames() {
    }

    public static String mask(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Bệnh nhân";
        }
        String[] parts = fullName.trim().split("\s+");
        StringBuilder masked = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            masked.append(' ').append(parts[i].substring(0, parts[i].offsetByCodePoints(0, 1))
                    .toUpperCase(Locale.ROOT)).append('.');
        }
        return masked.toString();
    }
}
