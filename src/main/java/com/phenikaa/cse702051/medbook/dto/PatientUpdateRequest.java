package com.phenikaa.cse702051.medbook.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cập nhật hồ sơ của chính bệnh nhân. Trường để trống (null) nghĩa là giữ nguyên; trường có
 * giá trị phải hợp lệ. Không có {@code patientCode}, {@code status}, {@code userId}: bệnh nhân
 * không được tự đổi các trường này. Chiều cao, cân nặng, số thẻ BHYT, bệnh nền và tiền sử gia đình là thông tin
 * bệnh nhân tự khai; bác sĩ phụ trách đọc được, lễ tân và quản trị viên không.
 */
public record PatientUpdateRequest(
        @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
        @Pattern(regexp = "^(?!\\s*$).+", message = "Họ tên không được để trống")
        String fullName,

        @Past(message = "Ngày sinh phải ở trong quá khứ")
        LocalDate dateOfBirth,

        @Pattern(regexp = "MALE|FEMALE|OTHER", message = "Giới tính phải là MALE, FEMALE hoặc OTHER")
        String genderCode,

        @Pattern(regexp = "^[0-9+()\\-\\s]{8,30}$", message = "Số điện thoại không hợp lệ")
        String phone,

        @Email(message = "Email không hợp lệ")
        @Size(max = 190, message = "Email tối đa 190 ký tự")
        String email,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address,

        @Size(max = 150, message = "Tên người liên hệ khẩn cấp tối đa 150 ký tự")
        String emergencyContactName,

        @Pattern(regexp = "^[0-9+()\\-\\s]{8,30}$", message = "Số điện thoại liên hệ khẩn cấp không hợp lệ")
        String emergencyContactPhone,

        @Pattern(regexp = "(?i)^(A|B|AB|O)[+-]?$", message = "Nhóm máu không hợp lệ (A, B, AB, O, có thể kèm + hoặc -)")
        String bloodType,

        @Size(max = 2000, message = "Thông tin dị ứng tối đa 2000 ký tự")
        String allergies,

        @Min(value = 30, message = "Chiều cao từ 30 đến 250 cm")
        @Max(value = 250, message = "Chiều cao từ 30 đến 250 cm")
        Short heightCm,

        @DecimalMin(value = "1.0", message = "Cân nặng từ 1 đến 400 kg")
        @DecimalMax(value = "400.0", message = "Cân nặng từ 1 đến 400 kg")
        BigDecimal weightKg,

        @Pattern(regexp = "^$|^[A-Za-z]{2}[0-9]{8,13}$",
                message = "Số thẻ BHYT gồm 2 chữ cái và 8 đến 13 chữ số, ví dụ DN4010123456789")
        String healthInsuranceNumber,

        @Size(max = 2000, message = "Bệnh nền tối đa 2000 ký tự")
        String chronicConditions,

        @Size(max = 2000, message = "Tiền sử gia đình tối đa 2000 ký tự")
        String familyHistory
) {
}
