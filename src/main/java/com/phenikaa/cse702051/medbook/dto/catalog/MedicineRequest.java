package com.phenikaa.cse702051.medbook.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Tạo hoặc sửa thuốc trong danh mục. {@code code} bắt buộc khi tạo và bất biến sau đó. */
public record MedicineRequest(
        @Size(max = 50, message = "Mã tối đa 50 ký tự")
        @Pattern(regexp = "^\\s*[A-Za-z0-9_-]*\\s*$",
                message = "Mã chỉ gồm chữ cái, chữ số, dấu gạch dưới và gạch ngang")
        String code,

        @NotBlank(message = "Tên thuốc không được để trống")
        @Size(max = 200, message = "Tên thuốc tối đa 200 ký tự")
        String name,

        @Size(max = 50, message = "Đơn vị tối đa 50 ký tự")
        String unit,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,

        @Pattern(regexp = "(?i)^\\s*(ACTIVE|INACTIVE)?\\s*$", message = "Trạng thái phải là ACTIVE hoặc INACTIVE")
        String status
) {
}
