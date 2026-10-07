package com.phenikaa.cse702051.medbook.dto.catalog;

import com.phenikaa.cse702051.medbook.model.Specialty;

/** Chuyên khoa trả ra cho trang công khai. */
public record SpecialtyDTO(
        Long id,
        String code,
        String name,
        String description,
        String status
) {
    public static SpecialtyDTO from(Specialty specialty) {
        return new SpecialtyDTO(specialty.getId(), specialty.getCode(), specialty.getName(),
                specialty.getDescription(), specialty.getStatus());
    }
}
