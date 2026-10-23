package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.User;

/**
 * Gửi liên kết đặt lại mật khẩu cho người dùng. Tách thành giao diện để kênh gửi (email của
 * {@code NotificationService} - Dev 3) có thể thay thế bản ghi log mặc định mà không đổi luồng
 * nghiệp vụ. {@code rawToken} chỉ tồn tại trong bộ nhớ và trong thông điệp gửi đi; CSDL chỉ lưu
 * bản băm.
 */
public interface PasswordResetNotifier {

    void sendResetLink(User user, String rawToken, LocalDateTime expiresAt);
}
