package com.phenikaa.cse702051.medbook.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.slot.SlotHoldDTO;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.GoneException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.SlotHold;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.SlotHoldRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUser;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Giữ chỗ có thời hạn và giải phóng tự động (khung T1).
 *
 * <h3>Nguyên tắc</h3>
 * <ol>
 * <li>Trạng thái giữ nằm ở CSDL (bảng {@code slot_holds}) kèm thời điểm hết hạn, không nằm trong bộ nhớ ứng dụng.</li>
 * <li>Mọi phép chuyển (giữ, đặt lịch, đổi lịch) bắt đầu bằng việc khóa hàng của khung giờ
 * ({@code SELECT … FOR UPDATE}), rồi mới kiểm lại trạng thái và bản giữ hiện có trong chính giao dịch đó. Các yêu
 * cầu trên cùng một khung giờ vì vậy chạy lần lượt; không có bước "kiểm ở ứng dụng rồi mới ghi".</li>
 * <li>Các giao dịch này chạy ở mức cô lập READ COMMITTED: sau khi lấy được khóa, lần đọc kế tiếp thấy dữ liệu mà
 * giao dịch giữ khóa trước đó vừa commit (ở REPEATABLE READ của MySQL, lần đọc đó có thể dùng ảnh chụp cũ).</li>
 * <li>UNIQUE {@code slot_holds.slot_id} là lớp bảo vệ cuối: CSDL từ chối bản giữ thứ hai trên cùng khung giờ.</li>
 * <li>Bản giữ đã quá hạn được coi là không tồn tại ở mọi nơi đọc và ghi, nên hệ thống vẫn đúng khi tác vụ dọn
 * ({@link SlotHoldCleanupJob}) không chạy.</li>
 * </ol>
 */
@Service
public class SlotHoldService {

    static final String HELD_BY_OTHER = "Khung giờ đang được người khác giữ chỗ. Vui lòng chọn khung giờ khác!";
    static final String HOLD_GONE = "Thời gian giữ chỗ đã hết. Vui lòng chọn lại khung giờ!";

    private final SlotHoldRepository holdRepository;
    private final AppointmentSlotRepository slotRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final Duration holdDuration;

    public SlotHoldService(
            SlotHoldRepository holdRepository,
            AppointmentSlotRepository slotRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            @Value("${medbook.booking.hold-minutes:10}") long holdMinutes) {
        this.holdRepository = holdRepository;
        this.slotRepository = slotRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.holdDuration = Duration.ofMinutes(Math.max(1, holdMinutes));
    }

    /**
     * Giữ khung giờ cho người gọi trong {@code medbook.booking.hold-minutes} phút. Người đang giữ gọi lại nhận đúng
     * bản giữ cũ (không gia hạn). Mỗi người chỉ có một bản giữ: bản giữ ở khung giờ khác được nhả trong cùng giao dịch.
     * Khung giờ không tồn tại → 404; đã đặt, đã gỡ, đã qua, bác sĩ ngừng nhận lịch hoặc người khác đang giữ → 409.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SlotHoldDTO hold(Long slotId) {
        Long userId = currentUserService.requireRole("PATIENT", "RECEPTIONIST").userId();
        // 1. một người, một yêu cầu giữ chỗ tại một thời điểm
        holdRepository.lockUser(userId);
        // 2. khóa hàng của khung giờ: các yêu cầu giữ, đặt, đổi trên khung giờ này xếp hàng ở đây
        AppointmentSlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung giờ khám!"));
        // 3. kiểm lại trạng thái khung giờ và bản giữ hiện có, ngay trong giao dịch đang giữ khóa
        assertHoldable(slot);
        LocalDateTime now = LocalDateTime.now();
        SlotHold current = holdRepository.findBySlotId(slotId).orElse(null);
        if (current != null) {
            if (current.getExpiresAt().isAfter(now)) {
                if (current.getUserId().equals(userId)) {
                    return toDTO(current, now);
                }
                throw new ConflictException(HELD_BY_OTHER);
            }
            // 4. bản giữ đã hết hạn nhường chỗ
            holdRepository.delete(current);
            holdRepository.flush();
        }
        holdRepository.deleteOthersOfUser(userId, slotId);
        // 5. ghi bản giữ mới; UNIQUE(slot_id) chặn trường hợp vẫn còn một bản giữ khác
        SlotHold created;
        try {
            created = holdRepository.saveAndFlush(SlotHold.builder()
                    .slotId(slotId)
                    .userId(userId)
                    .createdAt(now)
                    .expiresAt(now.plus(holdDuration))
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(HELD_BY_OTHER);
        }
        // 6. nhật ký cùng giao dịch: chỉ ghi khi giữ thành công, và không khóa chéo với hàng người dùng đang khóa
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.SLOT_HOLD,
                AuditActions.ENTITY_APPOINTMENT_SLOTS, slotId).with("slotId", slotId));
        return toDTO(created, now);
    }

    /** Nhả bản giữ của chính người gọi trên khung giờ; không có gì để nhả cũng không lỗi. */
    @Transactional
    public void release(Long slotId) {
        Long userId = currentUserService.requireRole("PATIENT", "RECEPTIONIST").userId();
        holdRepository.deleteBySlotAndUser(slotId, userId);
    }

    /**
     * Bước kiểm bản giữ của đặt lịch và đổi lịch. Người gọi PHẢI đang giữ khóa hàng của khung giờ
     * ({@code findByIdForUpdate}) trong cùng giao dịch.
     * <ul>
     * <li>Có {@code holdId}: phải đúng là bản giữ còn hạn của người gọi trên khung giờ này, nếu không → 410.</li>
     * <li>Không có {@code holdId}: người khác đang giữ còn hạn → 409; khung giờ trống thì đi tiếp như trước đây.</li>
     * </ul>
     * Qua được thì dòng giữ của khung giờ bị xóa: khung giờ sắp chuyển sang BOOKED trong cùng giao dịch, và nếu giao
     * dịch đó rollback thì dòng giữ trở lại nguyên vẹn.
     */
    @Transactional
    public void consumeForBooking(Long slotId, Long holdId) {
        Long userId = currentUserService.findCurrentUser().map(CurrentUser::userId).orElse(null);
        SlotHold current = holdRepository.findBySlotId(slotId).orElse(null);
        boolean active = current != null && current.getExpiresAt().isAfter(LocalDateTime.now());
        boolean mine = active && current.getUserId().equals(userId);
        if (holdId != null) {
            if (!mine || !current.getId().equals(holdId)) {
                throw new GoneException(HOLD_GONE);
            }
        } else if (active && !mine) {
            throw new ConflictException(HELD_BY_OTHER);
        }
        if (current != null) {
            holdRepository.delete(current);
            holdRepository.flush();
        }
    }

    /**
     * Bản giữ còn hạn trên một nhóm khung giờ, theo ID khung giờ. Dùng khi liệt kê giờ trống: khung giờ người khác
     * đang giữ bị ẩn, khung giờ của chính người xem được đánh dấu.
     */
    @Transactional(readOnly = true)
    public Map<Long, SlotHold> activeHolds(Collection<Long> slotIds) {
        if (slotIds.isEmpty()) {
            return Map.of();
        }
        return holdRepository.findBySlotIdInAndExpiresAtAfter(slotIds, LocalDateTime.now()).stream()
                .collect(Collectors.toMap(SlotHold::getSlotId, hold -> hold));
    }

    /**
     * Xóa các dòng đã hết hạn tính đến {@code now}. Chỉ để bảng không phình: tính đúng đắn không phụ thuộc việc này.
     *
     * @return số dòng đã xóa
     */
    @Transactional
    public int removeExpired(LocalDateTime now) {
        return holdRepository.deleteExpired(now);
    }

    private static void assertHoldable(AppointmentSlot slot) {
        boolean available = Boolean.TRUE.equals(slot.getIsAvailable()) && "AVAILABLE".equalsIgnoreCase(slot.getStatus());
        if (!available) {
            throw new ConflictException("Khung giờ này đã được đặt hoặc không còn khả dụng!");
        }
        if (!Boolean.TRUE.equals(slot.getDoctor().getIsActive())) {
            throw new ConflictException("Bác sĩ của khung giờ này hiện không nhận lịch!");
        }
        if (LocalDateTime.of(slot.getSlotDate(), slot.getStartTime()).isBefore(LocalDateTime.now())) {
            throw new ConflictException("Khung giờ này đã qua!");
        }
    }

    private static SlotHoldDTO toDTO(SlotHold hold, LocalDateTime now) {
        long remaining = Math.max(0, Duration.between(now, hold.getExpiresAt()).getSeconds());
        return new SlotHoldDTO(hold.getId(), hold.getSlotId(), hold.getExpiresAt(), remaining);
    }
}
