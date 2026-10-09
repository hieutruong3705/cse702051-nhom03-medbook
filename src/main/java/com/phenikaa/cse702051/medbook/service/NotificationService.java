package com.phenikaa.cse702051.medbook.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.phenikaa.cse702051.medbook.model.Appointment;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationService {

    /**
     * YCCN 24: Gửi thông báo/email xác nhận sau khi Transaction ĐÃ COMMIT thành công.
     * Tránh tình trạng giao dịch rollback nhưng vẫn gửi thông báo giả mạo.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAppointmentCreatedEvent(Appointment appointment) {
        log.info("Gửi thông báo thành công: Lịch khám #{} đã được đặt cho bệnh nhân #{}, bác sĩ #{}, khung giờ {}",
                appointment.getId(),
                appointment.getPatientId(),
                appointment.getDoctor().getId(),
                appointment.getSlot().getStartTime());
    }

    public void sendRescheduleNotification(Appointment appointment) {
        log.info("Gửi thông báo đổi lịch: Lịch khám #{} đã đổi sang khung giờ mới {}",
                appointment.getId(),
                appointment.getSlot().getStartTime());
    }

    public void sendCancellationNotification(Appointment appointment) {
        log.info("Gửi thông báo hủy lịch: Lịch khám #{} đã được hủy thành công và giải phóng slot.",
                appointment.getId());
    }
}