package com.phenikaa.cse702051.medbook.service;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.phenikaa.cse702051.medbook.dto.notification.NotificationMessage;
import com.phenikaa.cse702051.medbook.event.AppointmentBookedEvent;
import com.phenikaa.cse702051.medbook.event.AppointmentCancelledEvent;
import com.phenikaa.cse702051.medbook.event.AppointmentRescheduledEvent;

/**
 * Biến sự kiện lịch hẹn thành thông báo. Chỉ chạy SAU KHI giao dịch đặt, đổi, hủy lịch đã commit
 * ({@code AFTER_COMMIT}): giao dịch bị rollback (ví dụ slot vừa bị người khác đặt) thì không có thông báo nào.
 *
 * <p>Thông báo được lưu trong giao dịch mới của {@link NotificationService}; sau đó mới gọi
 * {@link NotificationSender}, ngoài mọi giao dịch. Mọi lỗi ở đây đều được bắt và ghi log mức WARN (không kèm
 * dữ liệu cá nhân) vì lịch hẹn đã được lưu, không được để lỗi thông báo biến thành lỗi của yêu cầu đặt lịch.
 */
@Component
public class AppointmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationListener.class);

    private final NotificationService notificationService;
    private final NotificationSender notificationSender;

    public AppointmentNotificationListener(
            NotificationService notificationService,
            NotificationSender notificationSender) {
        this.notificationService = notificationService;
        this.notificationSender = notificationSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBooked(AppointmentBookedEvent event) {
        deliver(event.appointmentId(), () -> notificationService.onBooked(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRescheduled(AppointmentRescheduledEvent event) {
        deliver(event.appointmentId(), () -> notificationService.onRescheduled(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(AppointmentCancelledEvent event) {
        deliver(event.appointmentId(), () -> notificationService.onCancelled(event));
    }

    private void deliver(Long appointmentId, Supplier<List<NotificationMessage>> creator) {
        List<NotificationMessage> created;
        try {
            created = creator.get();
        } catch (RuntimeException e) {
            log.warn("Không lưu được thông báo cho lịch hẹn #{}: {}", appointmentId, e.getClass().getSimpleName());
            return;
        }
        for (NotificationMessage message : created) {
            send(message);
        }
    }

    /** Gửi ra kênh ngoài; lỗi của kênh gửi không ảnh hưởng tới thông báo đã lưu hay các thông báo còn lại. */
    void send(NotificationMessage message) {
        try {
            notificationSender.send(message);
        } catch (RuntimeException e) {
            log.warn("Không gửi được thông báo #{} ra kênh ngoài: {}", message.notificationId(),
                    e.getClass().getSimpleName());
        }
    }
}
