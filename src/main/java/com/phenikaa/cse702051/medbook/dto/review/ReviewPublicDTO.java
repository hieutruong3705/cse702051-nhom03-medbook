package com.phenikaa.cse702051.medbook.dto.review;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.DoctorReview;

/**
 * Đánh giá trên trang công khai. Cố ý KHÔNG có mã bệnh nhân, mã lịch hẹn hay họ tên đầy đủ: chỉ có tên rút gọn
 * ({@link ReviewerNames#mask}). {@code edited = true} khi bệnh nhân đã sửa sau lần gửi đầu. {@code visitDate} là ngày
 * của lần khám được đánh giá, cho người đọc biết người viết đã thực sự đi khám.
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
        LocalDate visitDate,
        LocalDateTime createdAt
) {
    public static ReviewPublicDTO from(DoctorReview review) {
        return from(review, null);
    }

    public static ReviewPublicDTO from(DoctorReview review, LocalDate visitDate) {
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
                visitDate,
                review.getCreatedAt());
    }
}
