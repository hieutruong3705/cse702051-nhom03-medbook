package com.phenikaa.cse702051.medbook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.phenikaa.cse702051.medbook.dto.notification.NotificationMessage;

/**
 * Bản mặc định của {@link NotificationSender}: chưa gửi email/SMS thật, chỉ ghi một dòng log để vận hành biết
 * thông báo đã phát. Không ghi nội dung thông báo hay thông tin cá nhân vào log, chỉ ghi các mã định danh.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(NotificationMessage message) {
        log.info("Thông báo #{} loại {} cho người dùng #{} (lịch hẹn #{})",
                message.notificationId(), message.type(), message.userId(), message.appointmentId());
    }
}
