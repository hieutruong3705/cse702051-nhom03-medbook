package com.phenikaa.cse702051.medbook.exception;

import java.util.Map;

/**
 * Dữ liệu hợp lệ về cú pháp nhưng sai nghiệp vụ ở một trường cụ thể (ví dụ mật khẩu cũ không
 * đúng). HTTP 400, mã {@code VALIDATION_FAILED}, {@code details} là {tên trường → lý do} để
 * giao diện hiển thị ngay dưới ô nhập. Dùng 400 thay vì 401 để không bị hiểu là hết phiên.
 */
public class FieldValidationException extends ApiException {

    private final Map<String, String> details;

    public FieldValidationException(String field, String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
        this.details = Map.of(field, message);
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
