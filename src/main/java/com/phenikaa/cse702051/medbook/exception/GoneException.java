package com.phenikaa.cse702051.medbook.exception;

/** 410: tài nguyên từng có nhưng đã hết hiệu lực, ví dụ bản giữ chỗ đã hết hạn. */
public class GoneException extends ApiException {

    public GoneException(String message) {
        super(ErrorCode.GONE, message);
    }
}
