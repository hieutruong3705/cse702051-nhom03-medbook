package com.phenikaa.cse702051.medbook.dto.review;

import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.DoctorReview;

/**
 * Đánh giá trên trang công khai. Cố ý KHÔNG có mã bệnh nhân, mã lịch hẹn hay họ tên đầy đủ: chỉ có tên rút gọn
 * ({@link ReviewerNames#mask}). {@code edited = true} khi bệnh nhân đã sửa sau lần gửi đầu.
 */
public record ReviewPublicDTO(
        Long id,
        Long doctorId,
        String doctorName,
        String specialtyName,
        int rating,
        String comment,
        String reviewerName,
        boolean edited,
        LocalDateTime createdAt
) {
    public static ReviewPublicDTO from(DoctorReview review) {
        Doctor doctor = review.getDoctor();
        return new ReviewPublicDTO(
                review.getId(),
                doctor.getId(),
                doctor.getFullName(),
                doctor.getSpecialty() == null ? null : doctor.getSpecialty().getName(),
                review.getRating(),
                review.getComment(),
                ReviewerNames.mask(review.getPatient().getFullName()),
                review.getUpdatedAt() != null && review.getUpdatedAt().isAfter(review.getCreatedAt().plusSeconds(1)),
                review.getCreatedAt());
    }
}
