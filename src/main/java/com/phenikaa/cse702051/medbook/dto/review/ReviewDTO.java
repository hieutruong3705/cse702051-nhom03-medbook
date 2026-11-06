package com.phenikaa.cse702051.medbook.dto.review;

import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.DoctorReview;

/** Đánh giá của chính bệnh nhân đang đăng nhập; có trạng thái và lý do nếu đã bị quản trị viên ẩn. */
public record ReviewDTO(
        Long id,
        Long appointmentId,
        Long doctorId,
        String doctorName,
        String specialtyName,
        int rating,
        String comment,
        String status,
        String hiddenReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewDTO from(DoctorReview review) {
        Doctor doctor = review.getDoctor();
        return new ReviewDTO(
                review.getId(),
                review.getAppointmentId(),
                doctor.getId(),
                doctor.getFullName(),
                doctor.getSpecialty() == null ? null : doctor.getSpecialty().getName(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getHiddenReason(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }
}
