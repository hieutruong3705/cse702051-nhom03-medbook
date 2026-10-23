package com.phenikaa.cse702051.medbook.dto.prescription;

/**
 * Kê đơn mới hoặc sửa đơn. Lần khám lấy từ đường dẫn, mã đơn và thời điểm kê do server sinh, nên client không có
 * trường nào để chọn bản ghi đích (gửi thêm {@code id} hay {@code encounterId} đều bị bỏ qua). Khi sửa, trường
 * {@code null} nghĩa là giữ nguyên; {@code status} chỉ nhận {@code ACTIVE} hoặc {@code CANCELLED}.
 */
public record PrescriptionRequest(
        String notes,
        String status
) {
}
