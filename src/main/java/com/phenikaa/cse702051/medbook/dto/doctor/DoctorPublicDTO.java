package com.phenikaa.cse702051.medbook.dto.doctor;

import com.phenikaa.cse702051.medbook.service.MediaImageService;
import com.phenikaa.cse702051.medbook.dto.review.RatingSummaryDTO;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Specialty;

/**
 * Hồ sơ bác sĩ trên trang công khai. Cố ý KHÔNG có tài khoản ({@code userId}), số giấy phép hành nghề hay số
 * điện thoại: đó là dữ liệu nội bộ chỉ Admin thấy. Các trường giới thiệu (học vị, kinh nghiệm, đào tạo, thế mạnh,
 * quá trình công tác) do bác sĩ tự cập nhật; {@code averageRating} là {@code null} khi chưa có đánh giá đang hiện.
 */
public record DoctorPublicDTO(
        Long id,
        String fullName,
        Long specialtyId,
        String specialtyName,
        String bio,
        String academicTitle,
        Short yearsOfExperience,
        String education,
        String expertise,
        String workHistory,
        Double averageRating,
        long reviewCount,
        String avatarUrl
) {
    public static DoctorPublicDTO from(Doctor doctor) {
        return from(doctor, null);
    }

    public static DoctorPublicDTO from(Doctor doctor, RatingSummaryDTO rating) {
        Specialty specialty = doctor.getSpecialty();
        return new DoctorPublicDTO(
                doctor.getId(),
                doctor.getFullName(),
                specialty == null ? null : specialty.getId(),
                specialty == null ? null : specialty.getName(),
                doctor.getBio(),
                doctor.getAcademicTitle(),
                doctor.getYearsOfExperience(),
                doctor.getEducation(),
                doctor.getExpertise(),
                doctor.getWorkHistory(),
                rating == null ? null : rating.averageRating(),
                rating == null ? 0 : rating.reviewCount(),
                MediaImageService.urlOf(doctor.getAvatarImageId()));
    }
}
