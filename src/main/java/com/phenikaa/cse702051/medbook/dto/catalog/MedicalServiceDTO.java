package com.phenikaa.cse702051.medbook.dto.catalog;

import java.math.BigDecimal;

import com.phenikaa.cse702051.medbook.model.MedicalService;

/** Dịch vụ khám trả ra cho trang công khai, bước chọn dịch vụ khi đặt lịch và tab hóa đơn. */
public record MedicalServiceDTO(
        Long id,
        String code,
        String name,
        String description,
        Integer durationMinutes,
        BigDecimal price,
        String status
) {
    public static MedicalServiceDTO from(MedicalService service) {
        return new MedicalServiceDTO(service.getId(), service.getCode(), service.getName(),
                service.getDescription(), service.getDurationMinutes(), service.getPrice(), service.getStatus());
    }
}
