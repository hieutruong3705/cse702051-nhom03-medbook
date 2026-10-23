package com.phenikaa.cse702051.medbook.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;

/**
 * Cách duy nhất để gỡ slot khỏi lịch. Slot chưa từng gắn với lịch hẹn nào được xóa hẳn; slot đã có lịch sử (kể
 * cả lịch đã hủy) không xóa được vì khóa ngoại {@code appointments.slot_id} nên được giữ lại ở trạng thái
 * {@code CANCELLED}, {@code isAvailable = false} và không còn hiện ra ở đâu.
 *
 * <p>Người gọi phải bảo đảm không slot nào đang được lịch hẹn giữ chỗ, và phải gọi trong giao dịch của mình.
 * Việc xóa đi qua entity nên có kiểm tra {@code @Version}: slot vừa bị người khác đặt sẽ làm giao dịch thất bại
 * khi flush thay vì âm thầm mất lịch.
 */
@Component
public class SlotRetirement {

    public static final String STATUS_CANCELLED = "CANCELLED";

    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;

    public SlotRetirement(AppointmentSlotRepository slotRepository, AppointmentRepository appointmentRepository) {
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
    }

    /** @return số slot đã gỡ */
    public int retire(Collection<AppointmentSlot> slots) {
        if (slots.isEmpty()) {
            return 0;
        }
        List<Long> ids = slots.stream().map(AppointmentSlot::getId).toList();
        Set<Long> withHistory = new HashSet<>(appointmentRepository.findSlotIdsWithAnyAppointment(ids));
        List<AppointmentSlot> toDelete = new ArrayList<>();
        List<AppointmentSlot> toKeepAsHistory = new ArrayList<>();
        for (AppointmentSlot slot : slots) {
            if (withHistory.contains(slot.getId())) {
                slot.setIsAvailable(false);
                slot.setStatus(STATUS_CANCELLED);
                toKeepAsHistory.add(slot);
            } else {
                toDelete.add(slot);
            }
        }
        slotRepository.saveAll(toKeepAsHistory);
        slotRepository.deleteAll(toDelete);
        return slots.size();
    }
}
