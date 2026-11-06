package com.phenikaa.cse702051.medbook.dto.review;

import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.DoctorReview;
import com.phenikaa.cse702051.medbook.model.Patient;

/** Đánh giá cho trang kiểm duyệt của quản trị viên: có mã bệnh nhân để đối chiếu, không có dữ liệu lâm sàng. */
public record ReviewAdminDTO(
        Long id,
        Long appointmentId,
        Long doctorId,
        String doctorName,
        Long patientId,
        String patientCode,
        String patientName,
        int rating,
        String comment,
        String status,
        String hiddenReason,
        LocalDateTime hiddenAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewAdminDTO from(DoctorReview review) {
        Doctor doctor = review.getDoctor();
        Patient patient = review.getPatient();
        return new ReviewAdminDTO(
                review.getId(),
                review.getAppointmentId(),
                doctor.getId(),
                doctor.getFullName(),
                patient.getId(),
                patient.getPatientCode(),
                patient.getFullName(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getHiddenReason(),
                review.getHiddenAt(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }
}
