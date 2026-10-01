package com.phenikaa.cse702051.medbook.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.phenikaa.cse702051.medbook.security.PasswordPolicy;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Đăng ký tài khoản bệnh nhân. Cố ý KHÔNG có trường role/status: đăng ký luôn tạo tài khoản
 * PATIENT, client không thể tự gán quyền đặc biệt (trường lạ trong JSON bị bỏ qua).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "Tên đăng nhập chỉ gồm chữ cái, chữ số, dấu chấm, gạch dưới và gạch ngang")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    private String password;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 150, message = "Họ và tên tối đa 150 ký tự")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    @Size(max = 190, message = "Email tối đa 190 ký tự")
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^[0-9+()\\-\\s]{8,30}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Past(message = "Ngày sinh phải ở trong quá khứ")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    @Pattern(regexp = "(?i)^(MALE|FEMALE|OTHER)$", message = "Giới tính phải là MALE, FEMALE hoặc OTHER")
    private String genderCode; // MALE, FEMALE, OTHER

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    @Size(max = 150, message = "Tên người liên hệ khẩn cấp tối đa 150 ký tự")
    private String emergencyContactName;

    @Pattern(regexp = "^[0-9+()\\-\\s]{8,30}$", message = "Số điện thoại liên hệ khẩn cấp không hợp lệ")
    private String emergencyContactPhone;

    @Pattern(regexp = "(?i)^(A|B|AB|O)[+-]?$", message = "Nhóm máu không hợp lệ (A, B, AB, O, có thể kèm + hoặc -)")
    private String bloodType;

    @Size(max = 2000, message = "Thông tin dị ứng tối đa 2000 ký tự")
    private String allergies;
}
