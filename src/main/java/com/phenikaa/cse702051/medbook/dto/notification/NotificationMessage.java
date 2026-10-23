package com.phenikaa.cse702051.medbook.dto.notification;

/**
 * Một thông báo đã được lưu và sẵn sàng gửi ra kênh ngoài ứng dụng (email, SMS...) qua
 * {@code NotificationSender}. Chỉ chứa dữ liệu thuần để dùng được sau khi giao dịch đã kết thúc.
 */
public record NotificationMessage(
        Long notificationId,
        Long userId,
        String type,
        String title,
        String message,
        Long appointmentId
) {
}
