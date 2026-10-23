package com.phenikaa.cse702051.medbook.report;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;

/**
 * Dựng lịch hẹn ở ngày khám và trạng thái cho trước, thẳng vào CSDL, cho các test báo cáo. Mỗi lịch có slot riêng
 * (giờ bắt đầu tăng dần) nên không vướng ràng buộc "một slot một lịch đang hiệu lực".
 */
@Component
public class ReportFixtures {

    private static final AtomicInteger DAY = new AtomicInteger();
    private static final AtomicInteger MINUTE = new AtomicInteger();

    private final DoctorRepository doctors;
    private final AppointmentSlotRepository slots;
    private final AppointmentRepository appointments;

    public ReportFixtures(DoctorRepository doctors, AppointmentSlotRepository slots,
            AppointmentRepository appointments) {
        this.doctors = doctors;
        this.slots = slots;
        this.appointments = appointments;
    }

    /** Một ngày xa trong tương lai mà chưa test báo cáo nào dùng (cách nhau 40 ngày để khoảng lọc không chồng nhau). */
    public static LocalDate freshDay() {
        return LocalDate.now().plusDays(2000L + 40L * DAY.incrementAndGet());
    }

    public Appointment appointment(long doctorId, long patientId, LocalDate date, AppointmentStatus status) {
        return appointment(doctorId, patientId, date, status, null, null, null);
    }

    public Appointment appointment(long doctorId, long patientId, LocalDate date, AppointmentStatus status,
            Long serviceId, String notes, String cancelReason) {
        Doctor doctor = doctors.findById(doctorId).orElseThrow();
        return appointments.save(build(doctor, patientId, date, status, serviceId, notes, cancelReason));
    }

    /** Dựng nhanh nhiều lịch cùng bác sĩ, cùng ngày, cùng trạng thái. */
    public List<Appointment> appointments(long doctorId, long patientId, LocalDate date, AppointmentStatus status,
            int count) {
        Doctor doctor = doctors.findById(doctorId).orElseThrow();
        List<Appointment> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            created.add(build(doctor, patientId, date, status, null, null, null));
        }
        return appointments.saveAll(created);
    }

    private Appointment build(Doctor doctor, long patientId, LocalDate date, AppointmentStatus status,
            Long serviceId, String notes, String cancelReason) {
        // 00:00 + n phút, quay vòng trong ngày: mỗi lịch một slot riêng
        LocalTime start = LocalTime.MIN.plusMinutes(MINUTE.incrementAndGet() % 1430);
        AppointmentSlot slot = slots.save(AppointmentSlot.builder()
                .doctor(doctor)
                .slotDate(date)
                .startTime(start)
                .endTime(start.plusMinutes(5))
                .isAvailable(status == AppointmentStatus.CANCELLED)
                .status(status == AppointmentStatus.CANCELLED ? "AVAILABLE" : "BOOKED")
                .build());
        return Appointment.builder()
                .patientId(patientId)
                .doctor(doctor)
                .slot(slot)
                .serviceId(serviceId)
                .status(status)
                .notes(notes)
                .cancelReason(cancelReason)
                .build();
    }
}
