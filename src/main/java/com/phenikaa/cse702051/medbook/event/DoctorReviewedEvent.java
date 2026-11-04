package com.phenikaa.cse702051.medbook.event;

/** Bệnh nhân vừa gửi đánh giá cho một lần khám; phát trong giao dịch tạo đánh giá, xử lý sau khi commit. */
public record DoctorReviewedEvent(
        Long reviewId,
        Long appointmentId,
        Long doctorId,
        int rating
) {
}
