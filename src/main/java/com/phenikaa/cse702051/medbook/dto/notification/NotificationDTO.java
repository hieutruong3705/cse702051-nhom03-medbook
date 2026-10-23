package com.phenikaa.cse702051.medbook.dto.notification;

import java.time.LocalDateTime;

import com.phenikaa.cse702051.medbook.model.Notification;

/** Thông báo trả ra cho chính người nhận. {@code readAt} là {@code null} khi chưa đọc. */
public record NotificationDTO(
        Long id,
        String type,
        String title,
        String message,
        Long appointmentId,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
    public static NotificationDTO from(Notification notification) {
        return new NotificationDTO(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getAppointmentId(), notification.getCreatedAt(),
                notification.getReadAt());
    }
}
