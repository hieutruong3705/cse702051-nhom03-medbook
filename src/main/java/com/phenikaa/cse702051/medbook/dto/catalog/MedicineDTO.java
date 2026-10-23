package com.phenikaa.cse702051.medbook.dto.catalog;

import com.phenikaa.cse702051.medbook.model.Medicine;

/** Thuốc trong danh mục, dùng cho bác sĩ chọn khi kê đơn. */
public record MedicineDTO(
        Long id,
        String code,
        String name,
        String unit,
        String description,
        String status
) {
    public static MedicineDTO from(Medicine medicine) {
        return new MedicineDTO(medicine.getId(), medicine.getCode(), medicine.getName(), medicine.getUnit(),
                medicine.getDescription(), medicine.getStatus());
    }
}
