package com.phenikaa.cse702051.medbook.dto.clinic;

import java.util.List;

/**
 * Trang giới thiệu phòng khám. Thông tin liên hệ lấy từ cấu hình {@code medbook.clinic.*} và là dữ liệu mô phỏng;
 * các con số (bác sĩ, chuyên khoa, dịch vụ, điểm đánh giá) đếm trực tiếp từ CSDL.
 */
public record ClinicInfoDTO(
        String name,
        String introduction,
        String address,
        String phone,
        String email,
        List<String> workingHours,
        long doctorCount,
        long specialtyCount,
        long serviceCount,
        Double averageRating,
        long reviewCount
) {
}
