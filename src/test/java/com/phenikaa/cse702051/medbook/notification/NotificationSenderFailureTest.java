package com.phenikaa.cse702051.medbook.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.NotificationRepository;
import com.phenikaa.cse702051.medbook.service.NotificationSender;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-05.8: kênh gửi thông báo ra ngoài (email, SMS...) hỏng không được làm hỏng việc đặt lịch. Cần spy lên
 * {@link NotificationSender} nên chạy trong Spring context riêng.
 */
class NotificationSenderFailureTest extends AbstractApiTest {

    @MockitoSpyBean
    private NotificationSender sender;

    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private NotificationRepository notifications;

    @Test
    @DisplayName("Kênh gửi ném lỗi: đặt lịch vẫn 201, lịch hẹn tồn tại, thông báo trong ứng dụng vẫn được lưu cho cả hai")
    void bookingSurvivesSenderFailure() throws Exception {
        doThrow(new IllegalStateException("máy chủ thư không phản hồi")).when(sender).send(any());
        IsolatedDoctor doctor = data.isolatedDoctor("ntf_sender_doc");
        IsolatedPatient patient = data.isolatedPatient("ntf_sender_pat");
        AppointmentSlot slot = data.slot(doctor.doctorId(), LocalDate.now().plusDays(650), LocalTime.of(9, 0));

        MvcResult result = mvc.perform(post("/api/v1/appointments").header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":1}".formatted(slot.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andReturn();
        long appointmentId = new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asLong();

        assertEquals(AppointmentStatus.BOOKED, appointments.findById(appointmentId).orElseThrow().getStatus());
        assertEquals(2, notifications.findByAppointmentIdOrderByIdAsc(appointmentId).size(),
                "lỗi của người nhận thứ nhất không được chặn thông báo của người thứ hai");
        verify(sender, times(2)).send(any());
        // bệnh nhân vẫn đọc được lịch hẹn và thông báo của mình
        mvc.perform(get("/api/v1/appointments/" + appointmentId).header("Authorization", patient.token()))
                .andExpect(status().isOk());
        assertTrue(notifications.countByUserIdAndReadAtIsNull(patient.userId()) >= 1);
    }
}
