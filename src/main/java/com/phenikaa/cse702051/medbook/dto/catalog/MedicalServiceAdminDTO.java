package com.phenikaa.cse702051.medbook.dto.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.MedicalService;

/** Dịch vụ khám cho trang quản trị: thêm thời điểm tạo và sửa gần nhất. */
public record MedicalServiceAdminDTO(
        Long id,
        String code,
        String name,
        String description,
        Integer durationMinutes,
        BigDecimal price,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MedicalServiceAdminDTO from(MedicalService service) {
        return new MedicalServiceAdminDTO(service.getId(), service.getCode(), service.getName(),
                service.getDescription(), service.getDurationMinutes(), service.getPrice(), service.getStatus(),
                service.getCreatedAt(), service.getUpdatedAt());
    }
}
