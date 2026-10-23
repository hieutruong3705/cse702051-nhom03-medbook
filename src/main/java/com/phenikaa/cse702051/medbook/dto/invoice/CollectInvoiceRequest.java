package com.phenikaa.cse702051.medbook.dto.invoice;

import jakarta.validation.constraints.Size;

/** Ghi nhận đã thu tiền tại quầy. {@code note} chỉ để người thao tác ghi chú, không bắt buộc và không được lưu. */
public record CollectInvoiceRequest(
        @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
        String note
) {
}
