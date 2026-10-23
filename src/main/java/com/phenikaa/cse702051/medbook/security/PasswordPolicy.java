package com.phenikaa.cse702051.medbook.security;

/**
 * Chính sách mật khẩu dùng chung cho đăng ký, đổi và đặt lại mật khẩu: 8–100 ký tự, có ít nhất
 * một chữ cái và một chữ số. Hằng số là biểu thức hằng để dùng được trong annotation validation.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$";

    public static final String MESSAGE = "Mật khẩu phải từ 8 đến 100 ký tự, gồm ít nhất một chữ cái và một chữ số";

    public static boolean isValid(String password) {
        return password != null && password.matches(REGEX);
    }
}
