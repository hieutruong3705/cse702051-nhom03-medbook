package com.phenikaa.cse702051.medbook.exception;

/**
 * Vượt giới hạn tần suất (HTTP 429, mã {@code TOO_MANY_REQUESTS}). {@code retryAfterSeconds} được trả trong header
 * {@code Retry-After} và trong {@code details} để giao diện báo người dùng phải chờ bao lâu.
 */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
