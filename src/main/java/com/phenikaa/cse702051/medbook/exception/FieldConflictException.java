package com.phenikaa.cse702051.medbook.exception;

import java.util.Map;

/**
 * Giá trị của một trường trùng với bản ghi đã có (mã danh mục, tên đăng nhập, email, số giấy phép...). HTTP 409,
 * mã {@code CONFLICT}, {@code details} là {tên trường → lý do} để giao diện báo lỗi ngay tại ô nhập thay vì chỉ
 * hiện một thông báo chung.
 */
public class FieldConflictException extends ApiException {

    private final Map<String, String> details;

    public FieldConflictException(String field, String message) {
        super(ErrorCode.CONFLICT, message);
        this.details = Map.of(field, message);
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
