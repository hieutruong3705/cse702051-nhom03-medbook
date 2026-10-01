package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;

@Service
public class AppointmentSlotService {

    private final AppointmentSlotRepository appointmentSlotRepository;

    public AppointmentSlotService(AppointmentSlotRepository appointmentSlotRepository) {
        this.appointmentSlotRepository = appointmentSlotRepository;
    }

    /**
     * Sửa lỗi build của {@code develop}: controller đã gọi phương thức này nhưng
     * service chỉ là lớp rỗng. BE-03 sẽ thay bằng DTO và bỏ slot đã qua/giờ nghỉ.
     */
    @Transactional(readOnly = true)
    public List<AppointmentSlot> getAvailableSlots(Long doctorId, LocalDate slotDate) {
        return appointmentSlotRepository.findByDoctorIdAndSlotDateAndIsAvailableTrue(doctorId, slotDate);
    }
}
