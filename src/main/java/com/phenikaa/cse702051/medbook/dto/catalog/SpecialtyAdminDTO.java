package com.phenikaa.cse702051.medbook.dto.catalog;

import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Specialty;

/** Chuyên khoa cho trang quản trị: thêm thời điểm tạo và sửa gần nhất. */
public record SpecialtyAdminDTO(
        Long id,
        String code,
        String name,
        String description,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SpecialtyAdminDTO from(Specialty specialty) {
        return new SpecialtyAdminDTO(specialty.getId(), specialty.getCode(), specialty.getName(),
                specialty.getDescription(), specialty.getStatus(), specialty.getCreatedAt(),
                specialty.getUpdatedAt());
    }
}
