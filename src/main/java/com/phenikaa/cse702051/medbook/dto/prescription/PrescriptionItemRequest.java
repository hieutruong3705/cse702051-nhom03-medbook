package com.phenikaa.cse702051.medbook.dto.prescription;

import java.math.BigDecimal;

/**
 * Thêm hoặc sửa một dòng thuốc. Có {@code medicineId} thì thuốc phải đang hoạt động trong danh mục và tên được
 * chép từ danh mục; không có thì phải nhập {@code medicineName} (thuốc ngoài danh mục). Khi sửa, trường
 * {@code null} nghĩa là giữ nguyên.
 *
 * <p>{@code quantity} và {@code durationDays} nhận kiểu số thập phân để một giá trị như 1.5 bị từ chối rõ ràng
 * (400) thay vì bị âm thầm cắt thành 1.
 */
public record PrescriptionItemRequest(
        Long medicineId,
        String medicineName,
        String dosage,
        String frequency,
        BigDecimal durationDays,
        BigDecimal quantity,
        String instructions
) {
}
