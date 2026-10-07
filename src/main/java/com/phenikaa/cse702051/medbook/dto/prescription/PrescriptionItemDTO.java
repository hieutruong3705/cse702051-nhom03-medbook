package com.phenikaa.cse702051.medbook.dto.prescription;

import com.phenikaa.cse702051.medbook.model.PrescriptionItem;

/**
 * Một dòng thuốc trong đơn. {@code medicineId} là {@code null} với thuốc ngoài danh mục; {@code medicineName} là
 * tên tại thời điểm kê nên không đổi khi danh mục đổi tên hay ngừng dùng thuốc.
 */
public record PrescriptionItemDTO(
        Long id,
        Long medicineId,
        String medicineName,
        String dosage,
        String frequency,
        Integer durationDays,
        Integer quantity,
        String instructions
) {
    public static PrescriptionItemDTO from(PrescriptionItem item) {
        return new PrescriptionItemDTO(
                item.getId(),
                item.getMedicineId(),
                item.getMedicineName(),
                item.getDosage(),
                item.getFrequency(),
                item.getDurationDays(),
                item.getQuantity() == null ? null : item.getQuantity().intValue(),
                item.getInstructions());
    }
}
