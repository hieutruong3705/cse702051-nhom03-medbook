package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dọn các bản giữ chỗ đã hết hạn, mỗi phút một lần. Đây chỉ là việc dọn bảng: bản giữ hết hạn vốn đã bị bỏ qua ở
 * mọi truy vấn, nên khung giờ trở lại trống đúng lúc hết hạn dù tác vụ này chạy trễ hay không chạy.
 *
 * <p>Cấu hình: {@code medbook.booking.hold-cleanup-interval-ms} (mặc định 60000).
 */
@Component
public class SlotHoldCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SlotHoldCleanupJob.class);

    private final SlotHoldService slotHoldService;

    public SlotHoldCleanupJob(SlotHoldService slotHoldService) {
        this.slotHoldService = slotHoldService;
    }

    @Scheduled(
            fixedDelayString = "${medbook.booking.hold-cleanup-interval-ms:60000}",
            initialDelayString = "${medbook.booking.hold-cleanup-interval-ms:60000}")
    public void runOnSchedule() {
        try {
            int removed = slotHoldService.removeExpired(LocalDateTime.now());
            if (removed > 0) {
                log.info("Đã dọn {} bản giữ chỗ hết hạn", removed);
            }
        } catch (RuntimeException e) {
            log.warn("Lần dọn bản giữ chỗ hết hạn thất bại: {}", e.getClass().getSimpleName());
        }
    }
}
