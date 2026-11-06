package com.phenikaa.cse702051.medbook.dto.doctor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Bác sĩ tự cập nhật phần giới thiệu hiện trên trang công khai. Không có chuyên khoa, số giấy phép hay trạng thái:
 * các trường đó chỉ quản trị viên đổi được. Trường rỗng được lưu là chưa có thông tin.
 */
public record DoctorProfileUpdateRequest(
        @Size(max = 2000, message = "Giới thiệu tối đa 2000 ký tự")
        String bio,

        @Size(max = 100, message = "Học hàm, học vị tối đa 100 ký tự")
        String academicTitle,

        @Min(value = 0, message = "Số năm kinh nghiệm không được âm")
        @Max(value = 70, message = "Số năm kinh nghiệm tối đa 70")
        Short yearsOfExperience,

        @Size(max = 2000, message = "Quá trình đào tạo tối đa 2000 ký tự")
        String education,

        @Size(max = 2000, message = "Thế mạnh chuyên môn tối đa 2000 ký tự")
        String expertise,

        @Size(max = 2000, message = "Quá trình công tác tối đa 2000 ký tự")
        String workHistory
) {
}
