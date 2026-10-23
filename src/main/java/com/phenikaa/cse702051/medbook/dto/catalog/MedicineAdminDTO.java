package com.phenikaa.cse702051.medbook.dto.catalog;

import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Medicine;

/** Thuốc cho trang quản trị: thêm thời điểm tạo và sửa gần nhất. */
public record MedicineAdminDTO(
        Long id,
        String code,
        String name,
        String unit,
        String description,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MedicineAdminDTO from(Medicine medicine) {
        return new MedicineAdminDTO(medicine.getId(), medicine.getCode(), medicine.getName(), medicine.getUnit(),
                medicine.getDescription(), medicine.getStatus(), medicine.getCreatedAt(), medicine.getUpdatedAt());
    }
}
