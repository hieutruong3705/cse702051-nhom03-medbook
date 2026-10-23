package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.phenikaa.cse702051.medbook.dto.notification.NotificationMessage;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentReminderRepository;

/**
 * Nhắc lịch khám cho bệnh nhân ở hai mốc: trước giờ khám 24 giờ (giờ khám trong {@code (now + 2h, now + 24h]})
 * và 2 giờ (giờ khám trong {@code (now, now + 2h]}). Chỉ nhắc lịch còn BOOKED; mỗi lịch, mỗi mốc đúng một lần vì
 * quyền nhắc được giành bằng cập nhật có điều kiện trong {@link NotificationService#remind}. Đổi lịch xóa hai mốc
 * đã nhắc nên lịch mới được nhắc lại theo giờ mới.
 *
 * <p>Cấu hình: {@code medbook.reminders.enabled} (mặc định bật), {@code medbook.reminders.interval-ms}
 * (mặc định 5 phút), {@code medbook.reminders.batch-size} (mặc định 200 lịch mỗi mốc mỗi lần chạy).
 */
@Component
public class AppointmentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderJob.class);

    private final AppointmentReminderRepository reminderRepository;
    private final NotificationService notificationService;
    private final AppointmentNotificationListener delivery;
    private final boolean enabled;
    private final int batchSize;

    public AppointmentReminderJob(
            AppointmentReminderRepository reminderRepository,
            NotificationService notificationService,
            AppointmentNotificationListener delivery,
            @Value("${medbook.reminders.enabled:true}") boolean enabled,
            @Value("${medbook.reminders.batch-size:200}") int batchSize) {
        this.reminderRepository = reminderRepository;
        this.notificationService = notificationService;
        this.delivery = delivery;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(
            fixedDelayString = "${medbook.reminders.interval-ms:300000}",
            initialDelayString = "${medbook.reminders.initial-delay-ms:60000}")
    public void runOnSchedule() {
        if (!enabled) {
            return;
        }
        try {
            int sent = remindDue(LocalDateTime.now());
            if (sent > 0) {
                log.info("Đã tạo {} thông báo nhắc lịch khám", sent);
            }
        } catch (RuntimeException e) {
            log.warn("Lần chạy nhắc lịch khám thất bại: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Nhắc mọi lịch đến hạn tại thời điểm {@code now} (tối đa một lô cho mỗi mốc).
     *
     * @return số thông báo nhắc lịch đã tạo
     */
    public int remindDue(LocalDateTime now) {
        LocalDateTime in2Hours = now.plusHours(2);
        LocalDateTime in24Hours = now.plusHours(24);
        PageRequest limit = PageRequest.of(0, batchSize);

        List<Appointment> due24h = reminderRepository.findDueFor24h(AppointmentStatus.BOOKED,
                in2Hours.toLocalDate(), in2Hours.toLocalTime(),
                in24Hours.toLocalDate(), in24Hours.toLocalTime(), limit);
        List<Appointment> due2h = reminderRepository.findDueFor2h(AppointmentStatus.BOOKED,
                now.toLocalDate(), now.toLocalTime(),
                in2Hours.toLocalDate(), in2Hours.toLocalTime(), limit);

        int sent = 0;
        for (Appointment appointment : due24h) {
            sent += remind(appointment.getId(), NotificationService.REMINDER_24H, now);
        }
        for (Appointment appointment : due2h) {
            sent += remind(appointment.getId(), NotificationService.REMINDER_2H, now);
        }
        return sent;
    }

    private int remind(Long appointmentId, String type, LocalDateTime now) {
        Optional<NotificationMessage> created;
        try {
            created = notificationService.remind(appointmentId, type, now);
        } catch (RuntimeException e) {
            // Giao dịch nhắc đã rollback nên quyền nhắc được trả lại; lần chạy sau sẽ thử lại lịch này.
            log.warn("Không nhắc được lịch hẹn #{} ({}): {}", appointmentId, type, e.getClass().getSimpleName());
            return 0;
        }
        created.ifPresent(delivery::send);
        return created.isPresent() ? 1 : 0;
    }
}
