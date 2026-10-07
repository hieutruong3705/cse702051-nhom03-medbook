package com.phenikaa.cse702051.medbook.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Notification;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.NotificationRepository;
import com.phenikaa.cse702051.medbook.service.AppointmentReminderJob;
import com.phenikaa.cse702051.medbook.service.NotificationService;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * Nhắc lịch khám: mỗi mốc (24 giờ, 2 giờ) đúng một lần cho mỗi lịch còn BOOKED nằm trong cửa sổ thời gian. Test
 * gọi thẳng {@link AppointmentReminderJob#remindDue} với "bây giờ" tự chọn quanh một ngày xa trong tương lai, nên
 * không phụ thuộc đồng hồ thật và không đụng lịch hẹn của test khác.
 */
class AppointmentReminderJobTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger DAY = new AtomicInteger();

    @Autowired
    private AppointmentReminderJob job;
    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private AppointmentRepository appointments;

    /** Mỗi test một ngày riêng, cách nhau 10 ngày để cửa sổ nhắc của các test không chồng lên nhau. */
    private static LocalDate freshDay() {
        return LocalDate.now().plusDays(800L + 10L * DAY.incrementAndGet());
    }

    private long book(String token, long slotId) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/appointments").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":1}".formatted(slotId)))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private List<Notification> reminders(long appointmentId, String type) {
        return notifications.findByAppointmentIdOrderByIdAsc(appointmentId).stream()
                .filter(n -> type.equals(n.getType()))
                .toList();
    }

    @Test
    @DisplayName("AC-05.4 Chạy hai lần liên tiếp chỉ tạo một thông báo cho mỗi mốc 24 giờ và 2 giờ; chỉ bệnh nhân được nhắc")
    void eachMilestoneRemindedExactlyOnce() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rem_once_doc");
        IsolatedPatient patient = data.isolatedPatient("rem_once_pat");
        LocalDate day = freshDay();
        AppointmentSlot slot = data.slot(doctor.doctorId(), day, LocalTime.of(10, 0));
        long appointmentId = book(patient.token(), slot.getId());

        // còn 22 giờ: thuộc mốc 24 giờ
        LocalDateTime dayBefore = LocalDateTime.of(day.minusDays(1), LocalTime.of(12, 0));
        job.remindDue(dayBefore);
        job.remindDue(dayBefore);
        job.remindDue(dayBefore.plusMinutes(5));
        List<Notification> first = reminders(appointmentId, NotificationService.REMINDER_24H);
        assertEquals(1, first.size());
        assertEquals(patient.userId(), first.getFirst().getUser().getId());
        assertEquals("Nhắc lịch khám", first.getFirst().getTitle());
        assertTrue(first.getFirst().getMessage().contains("lúc 10:00 ngày"), first.getFirst().getMessage());
        assertEquals(0, reminders(appointmentId, NotificationService.REMINDER_2H).size());

        // còn 1 giờ 30 phút: thuộc mốc 2 giờ
        LocalDateTime sameMorning = LocalDateTime.of(day, LocalTime.of(8, 30));
        job.remindDue(sameMorning);
        job.remindDue(sameMorning);
        assertEquals(1, reminders(appointmentId, NotificationService.REMINDER_2H).size());
        assertEquals(1, reminders(appointmentId, NotificationService.REMINDER_24H).size());
        assertEquals("Sắp đến giờ khám", reminders(appointmentId, NotificationService.REMINDER_2H).getFirst().getTitle());

        Appointment saved = appointments.findById(appointmentId).orElseThrow();
        assertEquals(dayBefore, saved.getReminder24hSentAt());
        assertEquals(sameMorning, saved.getReminder2hSentAt());
        // bác sĩ không nhận thông báo nhắc lịch
        assertTrue(notifications.findByAppointmentIdOrderByIdAsc(appointmentId).stream()
                .filter(n -> n.getUser().getId().equals(doctor.userId()))
                .allMatch(n -> NotificationService.APPOINTMENT_BOOKED.equals(n.getType())));
    }

    @Test
    @DisplayName("AC-05.4 Lịch ngoài cửa sổ không được nhắc: còn quá xa, đúng giờ khám, đã qua giờ; biên 2 giờ thuộc mốc 2 giờ")
    void appointmentsOutsideWindowsAreNotReminded() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rem_window_doc");
        IsolatedPatient patient = data.isolatedPatient("rem_window_pat");
        LocalDate day = freshDay();
        AppointmentSlot slot = data.slot(doctor.doctorId(), day, LocalTime.of(10, 0));
        long appointmentId = book(patient.token(), slot.getId());

        job.remindDue(LocalDateTime.of(day.minusDays(2), LocalTime.of(9, 0)));   // còn 49 giờ
        job.remindDue(LocalDateTime.of(day.minusDays(1), LocalTime.of(9, 59)));  // còn 24 giờ 1 phút
        job.remindDue(LocalDateTime.of(day, LocalTime.of(10, 0)));               // đúng giờ khám
        job.remindDue(LocalDateTime.of(day, LocalTime.of(10, 30)));              // đã qua giờ
        assertEquals(0, reminders(appointmentId, NotificationService.REMINDER_24H).size());
        assertEquals(0, reminders(appointmentId, NotificationService.REMINDER_2H).size());
        assertNull(appointments.findById(appointmentId).orElseThrow().getReminder24hSentAt());

        // còn đúng 24 giờ: thuộc mốc 24 giờ (biên trên tính cả)
        job.remindDue(LocalDateTime.of(day.minusDays(1), LocalTime.of(10, 0)));
        assertEquals(1, reminders(appointmentId, NotificationService.REMINDER_24H).size());
        // còn đúng 2 giờ: thuộc mốc 2 giờ, không thuộc mốc 24 giờ
        job.remindDue(LocalDateTime.of(day, LocalTime.of(8, 0)));
        assertEquals(1, reminders(appointmentId, NotificationService.REMINDER_2H).size());
        assertEquals(1, reminders(appointmentId, NotificationService.REMINDER_24H).size());
    }

    @Test
    @DisplayName("AC-05.4 Lịch đã hủy, đang khám hoặc đã hoàn thành không được nhắc")
    void onlyBookedAppointmentsAreReminded() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rem_status_doc");
        IsolatedPatient patient = data.isolatedPatient("rem_status_pat");
        LocalDate day = freshDay();
        long cancelled = book(patient.token(), data.slot(doctor.doctorId(), day, LocalTime.of(9, 0)).getId());
        long inProgress = book(patient.token(), data.slot(doctor.doctorId(), day, LocalTime.of(10, 0)).getId());
        long completed = book(patient.token(), data.slot(doctor.doctorId(), day, LocalTime.of(11, 0)).getId());
        long booked = book(patient.token(), data.slot(doctor.doctorId(), day, LocalTime.of(12, 0)).getId());

        mvc.perform(patch("/api/v1/appointments/%d/cancel".formatted(cancelled))
                .header("Authorization", patient.token()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        setStatus(inProgress, AppointmentStatus.IN_PROGRESS);
        setStatus(completed, AppointmentStatus.COMPLETED);

        job.remindDue(LocalDateTime.of(day.minusDays(1), LocalTime.of(13, 0)));
        job.remindDue(LocalDateTime.of(day, LocalTime.of(8, 30)));
        job.remindDue(LocalDateTime.of(day, LocalTime.of(10, 30)));

        for (long id : new long[] { cancelled, inProgress, completed }) {
            assertEquals(0, reminders(id, NotificationService.REMINDER_24H).size());
            assertEquals(0, reminders(id, NotificationService.REMINDER_2H).size());
        }
        assertEquals(1, reminders(booked, NotificationService.REMINDER_24H).size());
        assertEquals(1, reminders(booked, NotificationService.REMINDER_2H).size());
    }

    @Test
    @DisplayName("AC-05.5 Đổi lịch rồi chạy nhắc: lịch được nhắc lại theo giờ mới")
    void rescheduledAppointmentIsRemindedAgain() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rem_move_doc");
        IsolatedPatient patient = data.isolatedPatient("rem_move_pat");
        LocalDate day = freshDay();
        AppointmentSlot original = data.slot(doctor.doctorId(), day, LocalTime.of(10, 0));
        AppointmentSlot moved = data.slot(doctor.doctorId(), day.plusDays(4), LocalTime.of(15, 0));
        long appointmentId = book(patient.token(), original.getId());

        job.remindDue(LocalDateTime.of(day.minusDays(1), LocalTime.of(12, 0)));
        assertEquals(1, reminders(appointmentId, NotificationService.REMINDER_24H).size());
        assertNotNull(appointments.findById(appointmentId).orElseThrow().getReminder24hSentAt());

        mvc.perform(patch("/api/v1/appointments/%d/reschedule".formatted(appointmentId))
                .header("Authorization", patient.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newSlotId\":%d}".formatted(moved.getId()))).andExpect(status().isOk());
        assertNull(appointments.findById(appointmentId).orElseThrow().getReminder24hSentAt(),
                "đổi lịch phải xóa mốc đã nhắc");

        // giờ cũ không còn được nhắc mốc 2 giờ; giờ mới được nhắc lại mốc 24 giờ
        job.remindDue(LocalDateTime.of(day, LocalTime.of(9, 0)));
        assertEquals(0, reminders(appointmentId, NotificationService.REMINDER_2H).size());
        job.remindDue(LocalDateTime.of(day.plusDays(3), LocalTime.of(16, 0)));
        List<Notification> again = reminders(appointmentId, NotificationService.REMINDER_24H);
        assertEquals(2, again.size());
        assertTrue(again.getLast().getMessage().contains("lúc 15:00 ngày"), again.getLast().getMessage());
    }

    private void setStatus(long appointmentId, AppointmentStatus status) {
        Appointment appointment = appointments.findById(appointmentId).orElseThrow();
        appointment.setStatus(status);
        appointments.save(appointment);
    }
}
