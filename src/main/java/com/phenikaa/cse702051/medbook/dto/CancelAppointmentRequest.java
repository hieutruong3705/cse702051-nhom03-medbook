package com.phenikaa.cse702051.medbook.dto;

import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(
        @Size(max = 500, message = "Lý do hủy tối đa 500 ký tự")
        String reason
) {
}
