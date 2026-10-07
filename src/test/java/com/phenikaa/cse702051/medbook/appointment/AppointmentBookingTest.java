package com.phenikaa.cse702051.medbook.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.event.AppointmentBookedEvent;
import com.phenikaa.cse702051.medbook.event.AppointmentCancelledEvent;
import com.phenikaa.cse702051.medbook.event.AppointmentRescheduledEvent;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData;
import com.phenikaa.cse702051.medbook.support.CommittedEventsCollector;

/**
 * BE-03 (YCCN-09…16): đặt/hủy/đổi lịch, máy trạng thái, quyền theo bản ghi, thông báo sau commit.
 * Mỗi test dùng slot tương lai riêng nên không ảnh hưởng nhau.
 */
class AppointmentBookingTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long SERVICE_ID = 1L; // seed: DV01 Khám tổng quát, ACTIVE

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private AppointmentSlotRepository slots;

    @Autowired
    private CommittedEventsCollector committed;

    private AppointmentSlot slot;

    @BeforeEach
    void freshSlot() {
        slot = data.futureSlot(ApiTestData.DOCTOR1_ID);
    }

    // ---------- helpers ----------

    private ResultActions book(String token, long slotId) throws Exception {
        return book(token, slotId, SERVICE_ID, "Đau đầu kéo dài");
    }

    private ResultActions book(String token, long slotId, long serviceId, String notes) throws Exception {
        return mvc.perform(post("/api/v1/appointments").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":%d,\"notes\":\"%s\"}".formatted(slotId, serviceId, notes)));
    }

    private long bookOk(String token, long slotId) throws Exception {
        MvcResult result = book(token, slotId).andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions patchJson(String token, String url, String body) throws Exception {
        return mvc.perform(patch(url).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private AppointmentSlot reload(AppointmentSlot s) {
        return slots.findById(s.getId()).orElseThrow();
    }

    private Appointment reloadAppointment(long id) {
        return appointments.findById(id).orElseThrow();
    }

    // ---------- đặt lịch ----------

    @Test
    @DisplayName("Đặt lịch: 201, bệnh nhân lấy từ JWT, slot chuyển BOOKED, khóa duy nhất active_slot_id được đặt")
    void bookSucceeds() throws Exception {
        book(patientToken(), slot.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.patientId").value((int) ApiTestData.PATIENT1_ID))
                .andExpect(jsonPath("$.doctorId").value((int) ApiTestData.DOCTOR1_ID))
                .andExpect(jsonPath("$.slotId").value(slot.getId()))
                .andExpect(jsonPath("$.serviceName").value("Khám tổng quát"))
                .andExpect(jsonPath("$.notes").value("Đau đầu kéo dài"))
                .andExpect(jsonPath("$.date").value(slot.getSlotDate().toString()));

        AppointmentSlot saved = reload(slot);
        assertEquals("BOOKED", saved.getStatus());
        assertFalse(saved.getIsAvailable());
        assertEquals(1, appointments.countActiveBySlotId(slot.getId()));
        Appointment appointment = appointments.findAll().stream()
                .filter(a -> a.getSlot().getId().equals(slot.getId())).findFirst().orElseThrow();
        assertEquals(slot.getId(), appointment.getActiveSlotId());
        assertEquals(SERVICE_ID, appointment.getServiceId());
    }

    @Test
    @DisplayName("patientId trong body bị bỏ qua: luôn đặt cho bệnh nhân của token (chống đặt hộ)")
    void patientIdInBodyIsIgnored() throws Exception {
        mvc.perform(post("/api/v1/appointments").header("Authorization", patientToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":%d,\"patientId\":%d}".formatted(
                        slot.getId(), SERVICE_ID, ApiTestData.PATIENT2_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value((int) ApiTestData.PATIENT1_ID));

        // và tham số query patientId cũng không có tác dụng
        AppointmentSlot other = data.futureSlot(ApiTestData.DOCTOR1_ID);
        mvc.perform(post("/api/v1/appointments?patientId=" + ApiTestData.PATIENT2_ID)
                .header("Authorization", patientToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":%d}".formatted(other.getId(), SERVICE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value((int) ApiTestData.PATIENT1_ID));
    }

    @Test
    @DisplayName("Đặt lịch: thiếu slot/dịch vụ → 400; slot không tồn tại → 404; dịch vụ không tồn tại → 400")
    void bookValidatesInput() throws Exception {
        mvc.perform(post("/api/v1/appointments").header("Authorization", patientToken())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.slotId").exists())
                .andExpect(jsonPath("$.details.serviceId").exists());
        book(patientToken(), 99999999L).andExpect(status().isNotFound());
        book(patientToken(), slot.getId(), 99999999L, "x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.serviceId").exists());
        assertEquals("AVAILABLE", reload(slot).getStatus(), "đặt lỗi không được chiếm slot");
    }

    @Test
    @DisplayName("Slot đã bị đặt → 409; slot đã qua → 409; chỉ bệnh nhân mới đặt được (bác sĩ/Admin 403)")
    void bookRejectsTakenPastAndWrongRole() throws Exception {
        long first = bookOk(patientToken(), slot.getId());
        assertTrue(first > 0);
        book(patient2Token(), slot.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        AppointmentSlot past = data.slot(ApiTestData.DOCTOR1_ID, LocalDate.now().minusDays(1), LocalTime.of(8, 0));
        book(patientToken(), past.getId()).andExpect(status().isConflict());
        assertEquals("AVAILABLE", reload(past).getStatus());

        AppointmentSlot another = data.futureSlot(ApiTestData.DOCTOR1_ID);
        book(doctorToken(), another.getId()).andExpect(status().isForbidden());
        book(adminToken(), another.getId()).andExpect(status().isForbidden());
    }

    // ---------- hủy lịch ----------

    @Test
    @DisplayName("Hủy lịch: 200 CANCELLED, lưu lý do, slot được giải phóng, khóa active_slot_id về NULL, có sự kiện sau commit")
    void cancelReleasesSlot() throws Exception {
        long id = bookOk(patientToken(), slot.getId());

        patchJson(patientToken(), "/api/v1/appointments/" + id + "/cancel", "{\"reason\":\"Bận việc đột xuất\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Bận việc đột xuất"))
                .andExpect(jsonPath("$.cancelledAt").exists());

        AppointmentSlot saved = reload(slot);
        assertEquals("AVAILABLE", saved.getStatus());
        assertTrue(saved.getIsAvailable());
        assertNull(reloadAppointment(id).getActiveSlotId());
        assertEquals(0, appointments.countActiveBySlotId(slot.getId()));
        assertTrue(committed.of(AppointmentCancelledEvent.class).stream().anyMatch(e -> e.appointmentId() == id));
    }

    @Test
    @DisplayName("Hủy lịch không cần body; hủy lần 2 → 422; lịch người khác → 403; không tồn tại → 404")
    void cancelRules() throws Exception {
        long id = bookOk(patientToken(), slot.getId());

        patchJson(patient2Token(), "/api/v1/appointments/" + id + "/cancel", "{}").andExpect(status().isForbidden());
        assertEquals("BOOKED", reload(slot).getStatus());

        mvc.perform(patch("/api/v1/appointments/" + id + "/cancel").header("Authorization", patientToken()))
                .andExpect(status().isOk());
        patchJson(patientToken(), "/api/v1/appointments/" + id + "/cancel", "{}").andExpect(status().isUnprocessableContent());
        patchJson(patientToken(), "/api/v1/appointments/99999999/cancel", "{}").andExpect(status().isNotFound());
        patchJson(doctorToken(), "/api/v1/appointments/" + id + "/cancel", "{}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Không hủy/đổi được lịch trong vòng 2 giờ trước giờ khám → 409, slot giữ nguyên")
    void cannotChangeTooCloseToStart() throws Exception {
        LocalDateTime soon = LocalDateTime.now().plusMinutes(70);
        AppointmentSlot near = data.slot(ApiTestData.DOCTOR1_ID, soon.toLocalDate(), soon.toLocalTime().withNano(0));
        long id = bookOk(patientToken(), near.getId());

        patchJson(patientToken(), "/api/v1/appointments/" + id + "/cancel", "{}").andExpect(status().isConflict());
        patchJson(patientToken(), "/api/v1/appointments/" + id + "/reschedule",
                "{\"newSlotId\":%d}".formatted(slot.getId())).andExpect(status().isConflict());

        assertEquals("BOOKED", reload(near).getStatus());
        assertEquals("AVAILABLE", reload(slot).getStatus());
        assertEquals(AppointmentStatus.BOOKED, reloadAppointment(id).getStatus());
    }

    @Test
    @DisplayName("Sau khi hủy, slot đặt lại được bởi bệnh nhân khác (không vướng ràng buộc duy nhất); luôn chỉ có 1 lịch đang hiệu lực")
    void slotCanBeRebookedAfterCancel() throws Exception {
        long first = bookOk(patientToken(), slot.getId());
        patchJson(patientToken(), "/api/v1/appointments/" + first + "/cancel", "{}").andExpect(status().isOk());

        long second = bookOk(patient2Token(), slot.getId());

        assertNotNull(reloadAppointment(second).getActiveSlotId());
        assertNull(reloadAppointment(first).getActiveSlotId());
        assertEquals(1, appointments.countActiveBySlotId(slot.getId()));
        assertEquals(2, appointments.findAll().stream().filter(a -> a.getSlot().getId().equals(slot.getId())).count(),
                "lịch đã hủy vẫn được giữ làm lịch sử");

        // và người đầu tiên cũng đặt lại được một slot khác rồi hủy/đặt nhiều lần
        AppointmentSlot other = data.futureSlot(ApiTestData.DOCTOR1_ID);
        long again = bookOk(patientToken(), other.getId());
        patchJson(patientToken(), "/api/v1/appointments/" + again + "/cancel", "{}").andExpect(status().isOk());
        bookOk(patientToken(), other.getId());
        assertEquals(1, appointments.countActiveBySlotId(other.getId()));
    }

    // ---------- đổi lịch ----------

    @Test
    @DisplayName("Đổi lịch: lịch chuyển sang slot mới, slot cũ được nhả, slot mới bị chiếm, có sự kiện sau commit")
    void rescheduleMovesAppointment() throws Exception {
        long id = bookOk(patientToken(), slot.getId());
        AppointmentSlot target = data.futureSlot(ApiTestData.DOCTOR2_ID);

        patchJson(patientToken(), "/api/v1/appointments/" + id + "/reschedule",
                "{\"newSlotId\":%d,\"reason\":\"Đổi bác sĩ\"}".formatted(target.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.slotId").value(target.getId()))
                .andExpect(jsonPath("$.doctorId").value((int) ApiTestData.DOCTOR2_ID));

        assertEquals("AVAILABLE", reload(slot).getStatus());
        assertEquals("BOOKED", reload(target).getStatus());
        Appointment moved = reloadAppointment(id);
        assertEquals(target.getId(), moved.getSlot().getId());
        assertEquals(target.getId(), moved.getActiveSlotId());
        assertEquals(1, appointments.countActiveBySlotId(target.getId()));
        assertEquals(0, appointments.countActiveBySlotId(slot.getId()));
        assertTrue(committed.of(AppointmentRescheduledEvent.class).stream().anyMatch(e -> e.appointmentId() == id));
    }

    @Test
    @DisplayName("Đổi sang slot đã có người đặt → 409 và lịch cũ + slot cũ GIỮ NGUYÊN (nguyên tử), không có sự kiện")
    void failedRescheduleKeepsOriginalAppointment() throws Exception {
        long id = bookOk(patientToken(), slot.getId());
        AppointmentSlot taken = data.futureSlot(ApiTestData.DOCTOR1_ID);
        bookOk(patient2Token(), taken.getId());
        long eventsBefore = committed.countFor(id);

        patchJson(patientToken(), "/api/v1/appointments/" + id + "/reschedule",
                "{\"newSlotId\":%d}".formatted(taken.getId()))
                .andExpect(status().isConflict());

        Appointment unchanged = reloadAppointment(id);
        assertEquals(slot.getId(), unchanged.getSlot().getId());
        assertEquals(slot.getId(), unchanged.getActiveSlotId());
        assertEquals(AppointmentStatus.BOOKED, unchanged.getStatus());
        assertEquals("BOOKED", reload(slot).getStatus(), "slot cũ không được nhả khi đổi thất bại");
        assertEquals(1, appointments.countActiveBySlotId(taken.getId()), "slot kia vẫn thuộc bệnh nhân 2");
        assertEquals(eventsBefore, committed.countFor(id), "rollback ⇒ không phát sự kiện thông báo");
    }

    @Test
    @DisplayName("Đổi lịch: cùng slot → 400; slot mới không tồn tại → 404; lịch người khác → 403; thiếu newSlotId → 400")
    void rescheduleRules() throws Exception {
        long id = bookOk(patientToken(), slot.getId());
        AppointmentSlot target = data.futureSlot(ApiTestData.DOCTOR1_ID);

        patchJson(patientToken(), "/api/v1/appointments/" + id + "/reschedule",
                "{\"newSlotId\":%d}".formatted(slot.getId())).andExpect(status().isBadRequest());
        patchJson(patientToken(), "/api/v1/appointments/" + id + "/reschedule", "{\"newSlotId\":99999999}")
                .andExpect(status().isNotFound());
        patchJson(patient2Token(), "/api/v1/appointments/" + id + "/reschedule",
                "{\"newSlotId\":%d}".formatted(target.getId())).andExpect(status().isForbidden());
        patchJson(patientToken(), "/api/v1/appointments/" + id + "/reschedule", "{}")
                .andExpect(status().isBadRequest());
        assertEquals("AVAILABLE", reload(target).getStatus());
        assertEquals("BOOKED", reload(slot).getStatus());
    }

    // ---------- trạng thái khám ----------

    @Test
    @DisplayName("Máy trạng thái: BOOKED → IN_PROGRESS → COMPLETED đúng thứ tự; nhảy cóc hoặc đi lùi → 422")
    void statusTransitions() throws Exception {
        long id = bookOk(patientToken(), slot.getId());
        String url = "/api/v1/appointments/" + id + "/status";

        patchJson(doctorToken(), url, "{\"status\":\"COMPLETED\"}").andExpect(status().isUnprocessableContent());
        patchJson(doctorToken(), url, "{\"status\":\"IN_PROGRESS\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        patchJson(doctorToken(), url, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isUnprocessableContent());
        patchJson(doctorToken(), url, "{\"status\":\"COMPLETED\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        patchJson(doctorToken(), url, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isUnprocessableContent());
        assertEquals(AppointmentStatus.COMPLETED, reloadAppointment(id).getStatus());
        assertEquals(1, appointments.countActiveBySlotId(slot.getId()), "lịch đã khám xong vẫn giữ slot");
    }

    @Test
    @DisplayName("Chỉ bác sĩ phụ trách mới đổi trạng thái (bác sĩ khác 403, bệnh nhân/Admin 403); trạng thái lạ bị từ chối")
    void statusAuthorizationAndValidation() throws Exception {
        long id = bookOk(patientToken(), slot.getId());
        String url = "/api/v1/appointments/" + id + "/status";

        patchJson(doctor2Token(), url, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isForbidden());
        patchJson(patientToken(), url, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isForbidden());
        patchJson(adminToken(), url, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isForbidden());
        patchJson(doctorToken(), url, "{\"status\":\"CANCELLED\"}").andExpect(status().isBadRequest());
        patchJson(doctorToken(), url, "{\"status\":\"BOOKED\"}").andExpect(status().isBadRequest());
        patchJson(doctorToken(), url, "{\"status\":\"KHONG_CO\"}").andExpect(status().isBadRequest());
        patchJson(doctorToken(), url, "{}").andExpect(status().isBadRequest());
        patchJson(doctorToken(), "/api/v1/appointments/99999999/status", "{\"status\":\"IN_PROGRESS\"}")
                .andExpect(status().isNotFound());
        assertEquals(AppointmentStatus.BOOKED, reloadAppointment(id).getStatus());
    }

    @Test
    @DisplayName("Đang khám hoặc đã khám xong thì bệnh nhân không hủy/đổi được (422)")
    void cannotCancelOnceExaminationStarted() throws Exception {
        long id = bookOk(patientToken(), slot.getId());
        patchJson(doctorToken(), "/api/v1/appointments/" + id + "/status", "{\"status\":\"IN_PROGRESS\"}")
                .andExpect(status().isOk());

        patchJson(patientToken(), "/api/v1/appointments/" + id + "/cancel", "{}").andExpect(status().isUnprocessableContent());
        AppointmentSlot target = data.futureSlot(ApiTestData.DOCTOR1_ID);
        patchJson(patientToken(), "/api/v1/appointments/" + id + "/reschedule",
                "{\"newSlotId\":%d}".formatted(target.getId())).andExpect(status().isUnprocessableContent());
        assertEquals("BOOKED", reload(slot).getStatus());
    }

    // ---------- đọc ----------

    @Test
    @DisplayName("GET /{id}: chủ lịch và bác sĩ phụ trách xem được; bệnh nhân khác, bác sĩ khác, Admin → 403; không tồn tại → 404")
    void readByIdIsScoped() throws Exception {
        long id = bookOk(patientToken(), slot.getId());
        String url = "/api/v1/appointments/" + id;

        mvc.perform(get(url).header("Authorization", patientToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get(url).header("Authorization", doctorToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.patientName").exists());
        mvc.perform(get(url).header("Authorization", patient2Token())).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", doctor2Token())).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", adminToken())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/appointments/99999999").header("Authorization", patientToken()))
                .andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /me: bệnh nhân chỉ thấy lịch của mình, bác sĩ chỉ thấy lịch khám của mình; lọc trạng thái/ngày; phân trang")
    void myAppointmentsAreScopedAndFiltered() throws Exception {
        LocalDate day = LocalDate.now().plusDays(400);
        AppointmentSlot s1 = data.slot(ApiTestData.DOCTOR1_ID, day, LocalTime.of(8, 0));
        AppointmentSlot s2 = data.slot(ApiTestData.DOCTOR1_ID, day, LocalTime.of(9, 0));
        AppointmentSlot s3 = data.slot(ApiTestData.DOCTOR2_ID, day.plusDays(1), LocalTime.of(8, 0));
        long mine1 = bookOk(patientToken(), s1.getId());
        long mine2 = bookOk(patientToken(), s2.getId());
        long others = bookOk(patient2Token(), s3.getId());
        patchJson(patientToken(), "/api/v1/appointments/" + mine2 + "/cancel", "{}").andExpect(status().isOk());

        String range = "from=%s&to=%s".formatted(day, day.plusDays(1));
        JsonNode patientView = JSON.readTree(mvc.perform(get("/api/v1/appointments/me?" + range + "&order=asc")
                .header("Authorization", patientToken())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(2, patientView.get("totalElements").asInt());
        assertEquals(mine1, patientView.get("content").get(0).get("id").asLong(), "order=asc: sớm nhất trước");
        assertFalse(patientView.get("content").toString().contains("\"id\":" + others + ","));

        mvc.perform(get("/api/v1/appointments/me?" + range + "&status=CANCELLED")
                .header("Authorization", patientToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine2));

        // bác sĩ 1 chỉ thấy lịch của mình (2 lịch: mine1, mine2), không thấy lịch của bác sĩ 2
        mvc.perform(get("/api/v1/appointments/me?" + range).header("Authorization", doctorToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/appointments/me?" + range).header("Authorization", doctor2Token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(others));

        // phân trang
        mvc.perform(get("/api/v1/appointments/me?" + range + "&size=1&page=1&order=asc")
                .header("Authorization", patientToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("GET /me: Admin không có lịch cá nhân (403); chưa đăng nhập 401")
    void myAppointmentsRequiresPatientOrDoctor() throws Exception {
        mvc.perform(get("/api/v1/appointments/me").header("Authorization", adminToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/appointments/me")).andExpect(status().isUnauthorized());
    }

    // ---------- thông báo sau commit ----------

    @Test
    @DisplayName("Đặt lịch thành công phát đúng một sự kiện AFTER_COMMIT; đặt thất bại (409) không phát sự kiện")
    void bookedEventOnlyAfterSuccessfulCommit() throws Exception {
        long id = bookOk(patientToken(), slot.getId());
        assertEquals(1, committed.of(AppointmentBookedEvent.class).stream().filter(e -> e.appointmentId() == id).count());
        AppointmentBookedEvent event = committed.of(AppointmentBookedEvent.class).stream()
                .filter(e -> e.appointmentId() == id).findFirst().orElseThrow();
        assertEquals(ApiTestData.PATIENT1_ID, event.patientId());
        assertEquals(slot.getSlotDate(), event.date());

        long before = committed.of(AppointmentBookedEvent.class).size();
        book(patient2Token(), slot.getId()).andExpect(status().isConflict());
        assertEquals(before, committed.of(AppointmentBookedEvent.class).size());
    }
}
