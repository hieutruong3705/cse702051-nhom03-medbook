package com.phenikaa.cse702051.medbook.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.Notification;
import com.phenikaa.cse702051.medbook.repository.NotificationRepository;
import com.phenikaa.cse702051.medbook.service.NotificationService;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * Đặt, đổi, hủy lịch sinh thông báo cho đúng người, chỉ sau khi giao dịch đã commit, và nội dung không lộ ghi chú
 * hay lý do hủy. Mỗi test dùng bác sĩ, bệnh nhân và ngày riêng.
 */
class AppointmentNotificationTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger DAY = new AtomicInteger();

    @Autowired
    private NotificationRepository notifications;

    private static LocalDate freshDay() {
        return LocalDate.now().plusDays(520L + DAY.incrementAndGet());
    }

    private MvcResult book(String token, long slotId, String notes) throws Exception {
        return mvc.perform(post("/api/v1/appointments").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":1,\"notes\":\"%s\"}".formatted(slotId, notes)))
                .andReturn();
    }

    private long bookOk(String token, long slotId) throws Exception {
        MvcResult result = book(token, slotId, "");
        assertEquals(201, result.getResponse().getStatus());
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private List<Notification> of(long appointmentId, long userId) {
        return notifications.findByAppointmentIdOrderByIdAsc(appointmentId).stream()
                .filter(n -> n.getUser().getId().equals(userId))
                .toList();
    }

    @Test
    @DisplayName("AC-05.1 Đặt lịch thành công: đúng một thông báo cho bệnh nhân và một cho bác sĩ, nêu giờ khám")
    void bookingNotifiesPatientAndDoctor() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("ntf_book_doc");
        IsolatedPatient patient = data.isolatedPatient("ntf_book_pat");
        LocalDate day = freshDay();
        AppointmentSlot slot = data.slot(doctor.doctorId(), day, LocalTime.of(9, 0));

        long appointmentId = bookOk(patient.token(), slot.getId());

        List<Notification> all = notifications.findByAppointmentIdOrderByIdAsc(appointmentId);
        assertEquals(2, all.size());
        Notification forPatient = of(appointmentId, patient.userId()).getFirst();
        Notification forDoctor = of(appointmentId, doctor.userId()).getFirst();
        assertEquals(1, of(appointmentId, patient.userId()).size());
        assertEquals(1, of(appointmentId, doctor.userId()).size());

        String date = "%02d/%02d/%d".formatted(day.getDayOfMonth(), day.getMonthValue(), day.getYear());
        assertEquals(NotificationService.APPOINTMENT_BOOKED, forPatient.getType());
        assertEquals("Đặt lịch khám thành công", forPatient.getTitle());
        assertEquals("Bạn đã đặt lịch khám với BS. Bác sĩ thử nghiệm ntf_book_doc lúc 09:00 ngày " + date + ".",
                forPatient.getMessage());
        assertEquals(NotificationService.APPOINTMENT_BOOKED, forDoctor.getType());
        assertEquals("Bệnh nhân Bệnh nhân thử nghiệm ntf_book_pat đã đặt lịch khám lúc 09:00 ngày " + date + ".",
                forDoctor.getMessage());
        assertTrue(forPatient.getReadAt() == null && forDoctor.getReadAt() == null);
    }

    @Test
    @DisplayName("AC-05.2 Đặt lịch thất bại vì slot vừa bị đặt (409): không tạo thêm thông báo nào")
    void failedBookingCreatesNoNotification() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("ntf_fail_doc");
        IsolatedPatient winner = data.isolatedPatient("ntf_fail_win");
        IsolatedPatient loser = data.isolatedPatient("ntf_fail_lose");
        AppointmentSlot slot = data.slot(doctor.doctorId(), freshDay(), LocalTime.of(9, 0));
        long appointmentId = bookOk(winner.token(), slot.getId());
        long doctorBefore = notifications.countByUserIdAndReadAtIsNull(doctor.userId());

        assertEquals(409, book(loser.token(), slot.getId(), "").getResponse().getStatus());

        assertEquals(0, notifications.countByUserIdAndReadAtIsNull(loser.userId()));
        assertEquals(doctorBefore, notifications.countByUserIdAndReadAtIsNull(doctor.userId()));
        assertEquals(2, notifications.findByAppointmentIdOrderByIdAsc(appointmentId).size());
    }

    @Test
    @DisplayName("AC-05.3 Hủy lịch: bác sĩ nhận thông báo hủy; bệnh nhân (người hủy) không nhận thêm")
    void cancellationNotifiesDoctor() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("ntf_cancel_doc");
        IsolatedPatient patient = data.isolatedPatient("ntf_cancel_pat");
        AppointmentSlot slot = data.slot(doctor.doctorId(), freshDay(), LocalTime.of(14, 30));
        long appointmentId = bookOk(patient.token(), slot.getId());

        mvc.perform(patch("/api/v1/appointments/%d/cancel".formatted(appointmentId))
                .header("Authorization", patient.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Bận việc\"}")).andExpect(status().isOk());

        List<Notification> forDoctor = of(appointmentId, doctor.userId());
        assertEquals(2, forDoctor.size());
        assertEquals(NotificationService.APPOINTMENT_CANCELLED, forDoctor.getLast().getType());
        assertEquals("Lịch khám đã bị hủy", forDoctor.getLast().getTitle());
        assertTrue(forDoctor.getLast().getMessage().contains("đã hủy lịch khám lúc 14:30 ngày"));
        assertEquals(1, of(appointmentId, patient.userId()).size(), "người hủy không nhận thông báo hủy");
    }

    @Test
    @DisplayName("AC-05.3 Đổi lịch: cả bệnh nhân và bác sĩ nhận thông báo nêu giờ mới")
    void rescheduleNotifiesBothWithNewTime() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("ntf_move_doc");
        IsolatedPatient patient = data.isolatedPatient("ntf_move_pat");
        LocalDate day = freshDay();
        AppointmentSlot from = data.slot(doctor.doctorId(), day, LocalTime.of(8, 0));
        AppointmentSlot to = data.slot(doctor.doctorId(), day.plusDays(1), LocalTime.of(15, 30));
        long appointmentId = bookOk(patient.token(), from.getId());

        mvc.perform(patch("/api/v1/appointments/%d/reschedule".formatted(appointmentId))
                .header("Authorization", patient.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newSlotId\":%d}".formatted(to.getId()))).andExpect(status().isOk());

        LocalDate newDay = day.plusDays(1);
        String newWhen = "lúc 15:30 ngày %02d/%02d/%d".formatted(newDay.getDayOfMonth(), newDay.getMonthValue(),
                newDay.getYear());
        Notification forPatient = of(appointmentId, patient.userId()).getLast();
        Notification forDoctor = of(appointmentId, doctor.userId()).getLast();
        assertEquals(NotificationService.APPOINTMENT_RESCHEDULED, forPatient.getType());
        assertEquals(NotificationService.APPOINTMENT_RESCHEDULED, forDoctor.getType());
        assertTrue(forPatient.getMessage().contains("đã được đổi sang " + newWhen), forPatient.getMessage());
        assertTrue(forDoctor.getMessage().contains("sang " + newWhen), forDoctor.getMessage());
        assertTrue(forDoctor.getMessage().contains("từ lúc 08:00 ngày"), forDoctor.getMessage());
        assertEquals(4, notifications.findByAppointmentIdOrderByIdAsc(appointmentId).size());
    }

    @Test
    @DisplayName("AC-05.9 Không thông báo nào chứa ghi chú của lịch hẹn hay lý do hủy")
    void notificationsNeverContainNotesOrCancelReason() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("ntf_clean_doc");
        IsolatedPatient patient = data.isolatedPatient("ntf_clean_pat");
        LocalDate day = freshDay();
        AppointmentSlot first = data.slot(doctor.doctorId(), day, LocalTime.of(8, 0));
        AppointmentSlot second = data.slot(doctor.doctorId(), day, LocalTime.of(10, 0));
        MvcResult booked = book(patient.token(), first.getId(), "GHICHU-BIMAT-7731 đau ngực");
        assertEquals(201, booked.getResponse().getStatus());
        long appointmentId = JSON.readTree(booked.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(patch("/api/v1/appointments/%d/reschedule".formatted(appointmentId))
                .header("Authorization", patient.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newSlotId\":%d,\"reason\":\"LYDO-DOI-5521\"}".formatted(second.getId())))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/appointments/%d/cancel".formatted(appointmentId))
                .header("Authorization", patient.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"LYDO-HUY-9902 xét nghiệm dương tính\"}")).andExpect(status().isOk());

        List<Notification> all = notifications.findByAppointmentIdOrderByIdAsc(appointmentId);
        assertEquals(5, all.size(), "đặt (2) + đổi (2) + hủy (1)");
        for (Notification notification : all) {
            String text = notification.getTitle() + " " + notification.getMessage();
            assertFalse(text.contains("GHICHU-BIMAT-7731"), text);
            assertFalse(text.contains("đau ngực"), text);
            assertFalse(text.contains("LYDO-DOI-5521"), text);
            assertFalse(text.contains("LYDO-HUY-9902"), text);
            assertFalse(text.contains("xét nghiệm"), text);
        }
    }
}
