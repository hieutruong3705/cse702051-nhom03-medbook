package com.phenikaa.cse702051.medbook.exception;

import java.time.LocalDateTime;

/**
 * Tài khoản đang bị khóa: hoặc tạm thời do đăng nhập sai quá số lần ({@code lockedUntil} có giá
 * trị), hoặc do Admin khóa ({@code lockedUntil} rỗng). HTTP 403, mã {@code ACCOUNT_LOCKED}.
 */
public class AccountLockedException extends ApiException {

    private final LocalDateTime lockedUntil;

    public AccountLockedException(String message, LocalDateTime lockedUntil) {
        super(ErrorCode.ACCOUNT_LOCKED, message);
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
}
