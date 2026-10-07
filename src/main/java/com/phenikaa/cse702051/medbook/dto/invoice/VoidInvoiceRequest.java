package com.phenikaa.cse702051.medbook.dto.invoice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Hủy một hóa đơn chưa thu. Bắt buộc có lý do để đối soát về sau. */
public record VoidInvoiceRequest(
        @NotBlank(message = "Phải nhập lý do hủy hóa đơn")
        @Size(max = 255, message = "Lý do hủy tối đa 255 ký tự")
        String reason
) {
}
