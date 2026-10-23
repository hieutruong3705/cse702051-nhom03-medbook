package com.phenikaa.cse702051.medbook.dto.catalog;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Tạo hoặc sửa dịch vụ khám. {@code code} bắt buộc khi tạo và bất biến sau đó; giá là tiền Việt Nam đồng với
 * tối đa hai chữ số thập phân.
 */
public record MedicalServiceRequest(
        @Size(max = 30, message = "Mã tối đa 30 ký tự")
        @Pattern(regexp = "^\\s*[A-Za-z0-9_-]*\\s*$",
                message = "Mã chỉ gồm chữ cái, chữ số, dấu gạch dưới và gạch ngang")
        String code,

        @NotBlank(message = "Tên dịch vụ không được để trống")
        @Size(max = 150, message = "Tên dịch vụ tối đa 150 ký tự")
        String name,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,

        @NotNull(message = "Phải nhập thời lượng khám")
        @Min(value = 5, message = "Thời lượng tối thiểu 5 phút")
        @Max(value = 480, message = "Thời lượng tối đa 480 phút")
        Integer durationMinutes,

        @NotNull(message = "Phải nhập giá dịch vụ")
        @DecimalMin(value = "0", message = "Giá không được âm")
        @DecimalMax(value = "99999999.99", message = "Giá tối đa 99.999.999,99")
        @Digits(integer = 8, fraction = 2, message = "Giá chỉ có tối đa hai chữ số thập phân")
        BigDecimal price,

        @Pattern(regexp = "(?i)^\\s*(ACTIVE|INACTIVE)?\\s*$", message = "Trạng thái phải là ACTIVE hoặc INACTIVE")
        String status
) {
}
