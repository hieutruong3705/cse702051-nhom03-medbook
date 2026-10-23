package com.phenikaa.cse702051.medbook.exception;

/**
 * Yêu cầu đúng cú pháp và đúng kiểu dữ liệu nhưng không thể thực hiện vì vi phạm quy tắc nghiệp vụ giữa nhiều
 * bản ghi (ví dụ ca làm việc chồng giờ, trùng ngày nghỉ, báo cáo vượt giới hạn số dòng). HTTP 422.
 */
public class UnprocessableEntityException extends ApiException {

    public UnprocessableEntityException(String message) {
        super(ErrorCode.UNPROCESSABLE_ENTITY, message);
    }
}
