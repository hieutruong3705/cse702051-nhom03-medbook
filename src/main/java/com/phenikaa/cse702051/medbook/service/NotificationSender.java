package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.dto.notification.NotificationMessage;

/**
 * Kênh gửi thông báo ra ngoài ứng dụng. Thông báo trong ứng dụng đã được lưu vào bảng {@code notifications}
 * trước khi phương thức này được gọi, và lời gọi luôn nằm ngoài giao dịch nghiệp vụ: bản cài đặt được phép chậm
 * hoặc ném lỗi mà không ảnh hưởng tới việc đặt, đổi, hủy lịch.
 *
 * <p>Điểm mở rộng: để gửi email thật, thêm một bean cài đặt interface này và đánh dấu {@code @Primary} (ví dụ
 * dùng SMTP); bản mặc định {@link LoggingNotificationSender} chỉ ghi log.
 */
public interface NotificationSender {

    void send(NotificationMessage message);
}
