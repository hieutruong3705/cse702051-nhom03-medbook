package com.phenikaa.cse702051.medbook.model;

/**
 * Trạng thái lịch hẹn (quyết định D3):
 * {@code BOOKED → IN_PROGRESS → COMPLETED}, nhánh {@code CANCELLED} (chỉ từ BOOKED).
 *
 * <ul>
 * <li>BOOKED — đã đặt, chưa khám (bệnh nhân còn hủy/đổi được).</li>
 * <li>IN_PROGRESS — bác sĩ đã bắt đầu khám.</li>
 * <li>COMPLETED — đã khám xong, đưa vào lịch sử khám.</li>
 * <li>CANCELLED — đã hủy, slot được giải phóng.</li>
 * </ul>
 */
public enum AppointmentStatus {
    BOOKED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    /** Lịch còn "giữ chỗ" một slot (mọi trạng thái trừ đã hủy). */
    public boolean holdsSlot() {
        return this != CANCELLED;
    }

    /** Bác sĩ chỉ được chuyển theo đúng thứ tự BOOKED → IN_PROGRESS → COMPLETED. */
    public boolean canTransitionTo(AppointmentStatus next) {
        return switch (this) {
            case BOOKED -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
