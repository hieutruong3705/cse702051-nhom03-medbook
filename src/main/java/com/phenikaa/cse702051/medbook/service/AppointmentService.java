package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;

    /**
     * YCCN 10, 11: Đặt lịch khám & Khóa chống trùng slot
     * Tiêu chí nghiệm thu: 100 request đồng thời -> chỉ 1 thành công, 99 trả về 409 Conflict
     */
    @Transactional
    public Appointment bookAppointment(Long patientId, Long slotId, String notes) {
        AppointmentSlot slot = appointmentSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khung giờ khám"));

        if (!Boolean.TRUE.equals(slot.getIsAvailable()) || "BOOKED".equalsIgnoreCase(slot.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Khung giờ này đã được đặt hoặc không khả dụng!");
        }

        // Cập nhật trạng thái slot để kích hoạt kiểm tra @Version
        slot.setIsAvailable(false);
        slot.setStatus("BOOKED");

        try {
            appointmentSlotRepository.saveAndFlush(slot);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Khung giờ vừa được đặt bởi người khác. Vui lòng chọn giờ khác!");
        }

        Appointment appointment = Appointment.builder()
                .patientId(patientId)
                .doctor(slot.getDoctor())
                .slot(slot)
                .status(AppointmentStatus.PENDING)
                .notes(notes)
                .build();

        return appointmentRepository.save(appointment);
    }

    /**
     * YCCN 12: Bệnh nhân hủy lịch khám - Giải phóng slot
     */
    @Transactional
    public Appointment cancelAppointment(Long appointmentId, Long patientId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn"));

        if (!appointment.getPatientId().equals(patientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác trên lịch hẹn này!");
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lịch hẹn đã được hủy trước đó!");
        }

        if (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể hủy lịch khám đã bắt đầu hoặc hoàn thành!");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);

        // Giải phóng slot về trạng thái khả dụng
        AppointmentSlot slot = appointment.getSlot();
        if (slot != null) {
            slot.setIsAvailable(true);
            slot.setStatus("AVAILABLE");
            appointmentSlotRepository.save(slot);
        }

        return appointmentRepository.save(appointment);
    }

    /**
     * YCCN 13: Đổi lịch khám - Tính nguyên tử (Atomic)
     * Thất bại ở bất kỳ khâu nào sẽ rollback toàn bộ, giữ nguyên lịch cũ
     */
    @Transactional
    public Appointment rescheduleAppointment(Long appointmentId, Long newSlotId, Long patientId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn"));

        if (!appointment.getPatientId().equals(patientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền sửa lịch hẹn này!");
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể đổi lịch hẹn đã hủy hoặc hoàn thành!");
        }

        AppointmentSlot oldSlot = appointment.getSlot();
        if (oldSlot.getId().equals(newSlotId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slot mới phải khác slot hiện tại!");
        }

        AppointmentSlot newSlot = appointmentSlotRepository.findById(newSlotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Khung giờ mới không tồn tại!"));

        if (!Boolean.TRUE.equals(newSlot.getIsAvailable()) || "BOOKED".equalsIgnoreCase(newSlot.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Khung giờ mới đã có người khác chọn!");
        }

        // 1. Giải phóng slot cũ
        oldSlot.setIsAvailable(true);
        oldSlot.setStatus("AVAILABLE");
        appointmentSlotRepository.save(oldSlot);

        // 2. Chiếm slot mới (kích hoạt kiểm tra @Version qua saveAndFlush)
        newSlot.setIsAvailable(false);
        newSlot.setStatus("BOOKED");
        try {
            appointmentSlotRepository.saveAndFlush(newSlot);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Khung giờ mới vừa có người nhanh tay đặt trước!");
        }

        // 3. Cập nhật thông tin lịch hẹn
        appointment.setSlot(newSlot);
        appointment.setDoctor(newSlot.getDoctor());
        return appointmentRepository.save(appointment);
    }

    /**
     * YCCN 16: Bác sĩ cập nhật trạng thái lịch khám (hoặc Dev 4 gọi khi tạo Encounter)
     */
    @Transactional
    public Appointment updateStatus(Long appointmentId, AppointmentStatus newStatus, Long doctorId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn"));

        if (!appointment.getDoctor().getId().equals(doctorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bác sĩ không phụ trách lịch khám này!");
        }

        appointment.setStatus(newStatus);
        return appointmentRepository.save(appointment);
    }

    public List<Appointment> getAppointmentsByPatient(Long patientId) {
        return appointmentRepository.findByPatientId(patientId);
    }

    public List<Appointment> getAppointmentsByDoctor(Long doctorId) {
        return appointmentRepository.findByDoctorId(doctorId);
    }

    public Appointment getAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn"));
    }
}