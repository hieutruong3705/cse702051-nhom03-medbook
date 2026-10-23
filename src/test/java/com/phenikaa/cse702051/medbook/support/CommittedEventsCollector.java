package com.phenikaa.cse702051.medbook.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.phenikaa.cse702051.medbook.event.AppointmentBookedEvent;
import com.phenikaa.cse702051.medbook.event.AppointmentCancelledEvent;
import com.phenikaa.cse702051.medbook.event.AppointmentRescheduledEvent;

/**
 * Giả lập bộ lắng nghe thông báo: chỉ nhận sự kiện lịch hẹn SAU KHI giao dịch đã commit
 * ({@code AFTER_COMMIT}). Giao dịch bị rollback thì không có gì được ghi lại — đúng yêu cầu "thông báo
 * chỉ gửi sau khi đặt/đổi/hủy thành công".
 */
@Component
public class CommittedEventsCollector {

    private final List<Object> events = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBooked(AppointmentBookedEvent event) {
        events.add(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRescheduled(AppointmentRescheduledEvent event) {
        events.add(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(AppointmentCancelledEvent event) {
        events.add(event);
    }

    public <T> List<T> of(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** Số sự kiện đã commit của một lịch hẹn cụ thể (theo appointmentId). */
    public long countFor(Long appointmentId) {
        return events.stream().filter(e -> switch (e) {
            case AppointmentBookedEvent b -> b.appointmentId().equals(appointmentId);
            case AppointmentRescheduledEvent r -> r.appointmentId().equals(appointmentId);
            case AppointmentCancelledEvent c -> c.appointmentId().equals(appointmentId);
            default -> false;
        }).count();
    }
}
