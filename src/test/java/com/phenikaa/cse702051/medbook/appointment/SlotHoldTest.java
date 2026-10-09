package com.phenikaa.cse702051.medbook.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.SlotHold;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.SlotHoldRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.service.SlotHoldService;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedReceptionist;

/**
 * SV-1: giữ chỗ có thời hạn trên khung giờ (khung T1). Chạy qua toàn bộ tầng controller, service, repository trên
 * CSDL thật của test. Bác sĩ, hai bệnh nhân và lễ tân dựng riêng cho lớp test này; mỗi test dùng khung giờ mới.
 * "Hết hạn" được dựng bằng cách lùi {@code expires_at} về quá khứ, không chờ và không gọi tác vụ dọn.
 */
class SlotHoldTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GONE_MESSAGE = "Thời gian giữ chỗ đã hết. Vui lòng chọn lại khung giờ!";

    private static IsolatedDoctor doctor;
    private static IsolatedPatient anna;
    private static IsolatedPatient binh;
    private static IsolatedReceptionist desk;

    @Autowired
    private SlotHoldRepository holds;
    @Autowired
    private AppointmentSlotRepository slots;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private DoctorRepository doctors;
    @Autowired
    private SlotHoldService slotHoldService;

    private AppointmentSlot slot;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    @BeforeEach
    void fixtures() {
        if (doctor == null) {
            String key = unique();
            doctor = data.isolatedDoctor("sh" + key);
            anna = data.isolatedPatient("sha" + key);
            binh = data.isolatedPatient("shb" + key);
            desk = data.isolatedReceptionist("sh" + key);
        }
        slot = data.futureSlot(doctor.doctorId());
    }

    // ---------- trợ giúp ----------

    private JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions hold(String token, long slotId) throws Exception {
        return mvc.perform(post("/api/v1/appointment-slots/" + slotId + "/hold").header("Authorization", token));
    }

    private long holdOk(String token, long slotId) throws Exception {
        return json(hold(token, slotId).andExpect(status().isOk())).get("holdId").asLong();
    }

    private ResultActions release(String token, long slotId) throws Exception {
        return mvc.perform(delete("/api/v1/appointment-slots/" + slotId + "/hold").header("Authorization", token));
    }

    private ResultActions book(String token, long slotId, Long holdId) throws Exception {
        String hold = holdId == null ? "" : ",\"holdId\":" + holdId;
        return mvc.perform(post("/api/v1/appointments").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":1%s}".formatted(slotId, hold)));
    }

    private ResultActions bookAtDesk(String token, long patientId, long slotId, Long holdId) throws Exception {
        String hold = holdId == null ? "" : ",\"holdId\":" + holdId;
        return mvc.perform(post("/api/v1/reception/appointments").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":%d,\"slotId\":%d,\"serviceId\":1%s}".formatted(patientId, slotId, hold)));
    }

    /** Lùi hạn của bản giữ trên khung giờ về quá khứ: từ đây nó phải được coi là không tồn tại. */
    private void expire(long slotId) {
        SlotHold hold = holds.findBySlotId(slotId).orElseThrow();
        hold.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        holds.saveAndFlush(hold);
    }

    private JsonNode available(String token, AppointmentSlot of) throws Exception {
        return json(mvc.perform(get("/api/v1/appointment-slots/available")
                .param("doctorId", String.valueOf(of.getDoctor().getId()))
                .param("date", of.getSlotDate().toString())
                .header("Authorization", token)).andExpect(status().isOk()));
    }

    private static JsonNode find(JsonNode list, long slotId) {
        for (JsonNode item : list) {
            if (item.get("id").asLong() == slotId) {
                return item;
            }
        }
        return null;
    }

    private long appointmentsOn(long slotId) {
        return appointments.findAll().stream().filter(a -> a.getSlot().getId().equals(slotId)).count();
    }

    // ---------- giữ chỗ ----------

    @Test
    @DisplayName("AC-S1.7 Bệnh nhân giữ một khung giờ trống: 200 với holdId, hạn 10 phút, đúng một bản audit SLOT_HOLD mang danh tính người giữ")
    void patientHoldsAFreeSlot() throws Exception {
        JsonNode body = json(hold(anna.token(), slot.getId()).andExpect(status().isOk()));

        assertTrue(body.get("holdId").asLong() > 0);
        assertEquals(slot.getId(), body.get("slotId").asLong());
        long remaining = body.get("remainingSeconds").asLong();
        assertTrue(remaining > 590 && remaining <= 600, "thời hạn mặc định là 10 phút: " + remaining);
        LocalDateTime expiresAt = LocalDateTime.parse(body.get("expiresAt").asText());
        assertTrue(expiresAt.isAfter(LocalDateTime.now().plusMinutes(9)));

        SlotHold row = holds.findBySlotId(slot.getId()).orElseThrow();
        assertEquals(anna.userId(), row.getUserId());
        assertEquals("AVAILABLE", slots.findById(slot.getId()).orElseThrow().getStatus(),
                "giữ chỗ không đổi trạng thái của khung giờ");

        List<AuditLog> audits = auditsOf(AuditActions.ENTITY_APPOINTMENT_SLOTS, slot.getId());
        assertEquals(1, audits.size());
        assertEquals(1, countAudits(AuditActions.ENTITY_APPOINTMENT_SLOTS, slot.getId(), AuditActions.SLOT_HOLD,
                anna.userId()));
        assertEquals("{\"slotId\":" + slot.getId() + "}", audits.get(0).getMetadataJson().replace(" ", ""),
                "metadata chỉ có slotId");
    }

    @Test
    @DisplayName("Người đang giữ gọi lại nhận đúng bản giữ cũ: cùng holdId, cùng hạn, không gia hạn, không thêm audit")
    void holdingAgainReturnsTheSameHoldWithoutExtending() throws Exception {
        JsonNode first = json(hold(anna.token(), slot.getId()).andExpect(status().isOk()));
        SlotHold row = holds.findBySlotId(slot.getId()).orElseThrow();
        row.setExpiresAt(row.getExpiresAt().minusMinutes(4)); // như thể đã giữ được 4 phút
        holds.saveAndFlush(row);

        JsonNode second = json(hold(anna.token(), slot.getId()).andExpect(status().isOk()));

        assertEquals(first.get("holdId").asLong(), second.get("holdId").asLong());
        long remaining = second.get("remainingSeconds").asLong();
        assertTrue(remaining > 350 && remaining <= 360, "còn khoảng 6 phút, không được gia hạn: " + remaining);
        assertEquals(1, holds.findAll().stream().filter(h -> h.getSlotId().equals(slot.getId())).count());
        assertEquals(1, auditsOf(AuditActions.ENTITY_APPOINTMENT_SLOTS, slot.getId()).size());
    }

    @Test
    @DisplayName("Lễ tân giữ được khung giờ; audit ghi lễ tân là người giữ")
    void receptionistHoldsASlot() throws Exception {
        hold(desk.token(), slot.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(slot.getId()));

        assertEquals(desk.userId(), holds.findBySlotId(slot.getId()).orElseThrow().getUserId());
        assertEquals(1, countAudits(AuditActions.ENTITY_APPOINTMENT_SLOTS, slot.getId(), AuditActions.SLOT_HOLD,
                desk.userId()));
    }

    @Test
    @DisplayName("AC-S1.2 Khung giờ đang bị giữ: người khác giữ 409, người khác đặt 409, không sinh lịch hẹn, bản giữ còn nguyên")
    void aHeldSlotRejectsOtherUsers() throws Exception {
        long holdId = holdOk(anna.token(), slot.getId());

        hold(binh.token(), slot.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Khung giờ đang được người khác giữ chỗ. Vui lòng chọn khung giờ khác!"));
        hold(desk.token(), slot.getId()).andExpect(status().isConflict());
        book(binh.token(), slot.getId(), null).andExpect(status().isConflict());
        bookAtDesk(desk.token(), binh.patientId(), slot.getId(), null).andExpect(status().isConflict());

        assertEquals(0, appointmentsOn(slot.getId()));
        assertEquals("AVAILABLE", slots.findById(slot.getId()).orElseThrow().getStatus());
        assertEquals(holdId, holds.findBySlotId(slot.getId()).orElseThrow().getId());
        assertEquals(1, auditsOf(AuditActions.ENTITY_APPOINTMENT_SLOTS, slot.getId()).size(),
                "lần giữ thất bại không ghi SLOT_HOLD");
    }

    @Test
    @DisplayName("AC-S1.2 Danh sách giờ trống: người khác và khách không thấy khung giờ đang bị giữ; người giữ thấy kèm heldByMe và holdExpiresAt")
    void listingHidesHeldSlotsFromOthers() throws Exception {
        AppointmentSlot free = data.slot(doctor.doctorId(), slot.getSlotDate(), LocalTime.of(11, 0));
        holdOk(anna.token(), slot.getId());

        JsonNode mine = available(anna.token(), slot);
        JsonNode held = find(mine, slot.getId());
        assertTrue(held != null && held.get("heldByMe").asBoolean());
        assertFalse(held.get("holdExpiresAt").isNull());
        JsonNode untouched = find(mine, free.getId());
        assertTrue(untouched != null && !untouched.get("heldByMe").asBoolean());
        assertTrue(untouched.get("holdExpiresAt").isNull());

        JsonNode others = available(binh.token(), slot);
        assertEquals(null, find(others, slot.getId()), "người khác không thấy khung giờ đang bị giữ");
        assertTrue(find(others, free.getId()) != null);
        assertEquals(null, find(available(desk.token(), slot), slot.getId()));

        JsonNode guest = json(mvc.perform(get("/api/v1/doctors/" + doctor.doctorId() + "/slots")
                .param("date", slot.getSlotDate().toString())).andExpect(status().isOk()));
        assertEquals(null, find(guest, slot.getId()), "khách cũng không thấy");
        assertTrue(find(guest, free.getId()) != null);
    }

    @Test
    @DisplayName("AC-S1.3 Hết hạn mà tác vụ dọn chưa chạy: khung giờ trở lại danh sách của người khác, và người khác giữ được")
    void anExpiredHoldNoLongerBlocksHolding() throws Exception {
        long expired = holdOk(anna.token(), slot.getId());
        expire(slot.getId());

        JsonNode item = find(available(binh.token(), slot), slot.getId());
        assertTrue(item != null && !item.get("heldByMe").asBoolean(), "bản giữ hết hạn coi như không có");
        JsonNode forOwner = find(available(anna.token(), slot), slot.getId());
        assertTrue(forOwner != null && !forOwner.get("heldByMe").asBoolean(), "người giữ cũ cũng không còn giữ");

        long fresh = holdOk(binh.token(), slot.getId());

        assertNotEquals(expired, fresh);
        assertEquals(binh.userId(), holds.findBySlotId(slot.getId()).orElseThrow().getUserId());
        hold(anna.token(), slot.getId()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("AC-S1.3 Hết hạn mà tác vụ dọn chưa chạy: người khác đặt lịch được, dòng giữ cũ bị xóa")
    void anExpiredHoldNoLongerBlocksBooking() throws Exception {
        holdOk(anna.token(), slot.getId());
        expire(slot.getId());

        book(binh.token(), slot.getId(), null).andExpect(status().isCreated());

        assertEquals("BOOKED", slots.findById(slot.getId()).orElseThrow().getStatus());
        assertTrue(holds.findBySlotId(slot.getId()).isEmpty());
    }

    @Test
    @DisplayName("AC-S1.5 Giữ khung giờ thứ hai thì khung giờ thứ nhất được nhả và người khác giữ được ngay")
    void holdingASecondSlotReleasesTheFirst() throws Exception {
        AppointmentSlot second = data.futureSlot(doctor.doctorId());
        holdOk(anna.token(), slot.getId());

        holdOk(anna.token(), second.getId());

        assertTrue(holds.findBySlotId(slot.getId()).isEmpty());
        assertEquals(1, holds.findAll().stream().filter(h -> h.getUserId().equals(anna.userId())).count(),
                "mỗi người tối đa một bản giữ");
        hold(binh.token(), slot.getId()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-S1.5 Nhả chủ động trả 204, gọi lại vẫn 204; không nhả được bản giữ của người khác")
    void releaseIsIdempotentAndOnlyForTheOwner() throws Exception {
        long holdId = holdOk(anna.token(), slot.getId());

        release(binh.token(), slot.getId()).andExpect(status().isNoContent());
        release(desk.token(), slot.getId()).andExpect(status().isNoContent());
        assertEquals(holdId, holds.findBySlotId(slot.getId()).orElseThrow().getId(), "bản giữ của người khác còn nguyên");

        release(anna.token(), slot.getId()).andExpect(status().isNoContent());
        assertTrue(holds.findBySlotId(slot.getId()).isEmpty());
        release(anna.token(), slot.getId()).andExpect(status().isNoContent());
        release(anna.token(), 999_999_999L).andExpect(status().isNoContent());

        assertTrue(find(available(binh.token(), slot), slot.getId()) != null, "nhả xong thì người khác thấy lại");
    }

    @Test
    @DisplayName("Giữ khung giờ không tồn tại 404; đã đặt, đã gỡ, đã qua hoặc bác sĩ ngừng nhận lịch 409; không dòng giữ nào được tạo")
    void holdRejectsSlotsThatCannotBeBooked() throws Exception {
        hold(anna.token(), 999_999_999L).andExpect(status().isNotFound());

        AppointmentSlot booked = data.futureSlot(doctor.doctorId());
        book(binh.token(), booked.getId(), null).andExpect(status().isCreated());
        hold(anna.token(), booked.getId()).andExpect(status().isConflict());

        AppointmentSlot removed = data.futureSlot(doctor.doctorId());
        removed.setStatus("CANCELLED");
        removed.setIsAvailable(false);
        slots.saveAndFlush(removed);
        hold(anna.token(), removed.getId()).andExpect(status().isConflict());

        AppointmentSlot past = data.slot(doctor.doctorId(), LocalDate.now().minusDays(1), LocalTime.of(9, 0));
        hold(anna.token(), past.getId()).andExpect(status().isConflict());

        IsolatedDoctor retired = data.isolatedDoctor("shr" + unique());
        AppointmentSlot ofRetired = data.futureSlot(retired.doctorId());
        Doctor profile = doctors.findById(retired.doctorId()).orElseThrow();
        profile.setIsActive(false);
        doctors.saveAndFlush(profile);
        hold(anna.token(), ofRetired.getId()).andExpect(status().isConflict());

        for (AppointmentSlot each : List.of(booked, removed, past, ofRetired)) {
            assertTrue(holds.findBySlotId(each.getId()).isEmpty());
        }
    }

    @Test
    @DisplayName("Khách 401; bác sĩ và quản trị viên 403 ở cả hai đường /hold")
    void onlyPatientsAndReceptionistsMayHold() throws Exception {
        String url = "/api/v1/appointment-slots/" + slot.getId() + "/hold";
        mvc.perform(post(url)).andExpect(status().isUnauthorized());
        mvc.perform(delete(url)).andExpect(status().isUnauthorized());
        for (String token : List.of(doctorToken(), doctor.token(), adminToken())) {
            mvc.perform(post(url).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(delete(url).header("Authorization", token)).andExpect(status().isForbidden());
        }
        assertTrue(holds.findBySlotId(slot.getId()).isEmpty());
    }

    // ---------- đặt lịch với bản giữ ----------

    @Test
    @DisplayName("AC-S1.4 Đặt lịch với holdId còn hạn: 201 và dòng giữ biến mất")
    void bookingWithAValidHoldSucceedsAndClearsIt() throws Exception {
        long holdId = holdOk(anna.token(), slot.getId());

        book(anna.token(), slot.getId(), holdId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"));

        assertTrue(holds.findBySlotId(slot.getId()).isEmpty());
        assertEquals("BOOKED", slots.findById(slot.getId()).orElseThrow().getStatus());
        assertEquals(1, appointmentsOn(slot.getId()));
    }

    @Test
    @DisplayName("AC-S1.4 Đặt lịch với holdId đã hết hạn: 410 GONE với thông điệp giữ chỗ, không tạo lịch hẹn, khung giờ vẫn trống")
    void bookingWithAnExpiredHoldIsGone() throws Exception {
        long holdId = holdOk(anna.token(), slot.getId());
        expire(slot.getId());

        book(anna.token(), slot.getId(), holdId).andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.code").value("GONE"))
                .andExpect(jsonPath("$.message").value(GONE_MESSAGE));

        assertEquals(0, appointmentsOn(slot.getId()));
        assertEquals("AVAILABLE", slots.findById(slot.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("holdId không còn (đã nhả), của người khác hoặc của khung giờ khác: 410, không tạo lịch hẹn")
    void bookingWithAHoldThatIsNotMineIsGone() throws Exception {
        long released = holdOk(anna.token(), slot.getId());
        release(anna.token(), slot.getId()).andExpect(status().isNoContent());
        book(anna.token(), slot.getId(), released).andExpect(status().isGone());

        long ofBinh = holdOk(binh.token(), slot.getId());
        book(anna.token(), slot.getId(), ofBinh).andExpect(status().isGone());

        AppointmentSlot other = data.futureSlot(doctor.doctorId());
        long ofAnna = holdOk(anna.token(), other.getId());
        AppointmentSlot third = data.futureSlot(doctor.doctorId());
        book(anna.token(), third.getId(), ofAnna).andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value(GONE_MESSAGE));

        assertEquals(0, appointmentsOn(slot.getId()) + appointmentsOn(other.getId()) + appointmentsOn(third.getId()));
        assertEquals(ofBinh, holds.findBySlotId(slot.getId()).orElseThrow().getId(), "bản giữ của người khác còn nguyên");
    }

    @Test
    @DisplayName("AC-S1.8 Không gửi holdId: khung giờ trống đặt như cũ; người đang giữ đặt không kèm holdId vẫn được và dòng giữ bị xóa")
    void bookingWithoutHoldIdStillWorks() throws Exception {
        book(binh.token(), slot.getId(), null).andExpect(status().isCreated());

        AppointmentSlot held = data.futureSlot(doctor.doctorId());
        holdOk(anna.token(), held.getId());
        book(anna.token(), held.getId(), null).andExpect(status().isCreated());

        assertTrue(holds.findBySlotId(held.getId()).isEmpty());
    }

    @Test
    @DisplayName("Hủy lịch xong thì khung giờ giữ lại được")
    void aCancelledBookingFreesTheSlotForHolding() throws Exception {
        long holdId = holdOk(anna.token(), slot.getId());
        long appointmentId = json(book(anna.token(), slot.getId(), holdId).andExpect(status().isCreated()))
                .get("id").asLong();
        hold(binh.token(), slot.getId()).andExpect(status().isConflict());

        mvc.perform(patch("/api/v1/appointments/" + appointmentId + "/cancel").header("Authorization", anna.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"thử giữ chỗ\"}"))
                .andExpect(status().isOk());

        hold(binh.token(), slot.getId()).andExpect(status().isOk());
    }

    // ---------- đổi lịch với bản giữ ----------

    @Test
    @DisplayName("Đổi lịch: khung giờ mới do người khác giữ 409, holdId hết hạn 410, lịch cũ giữ nguyên; holdId còn hạn thì đổi được và dòng giữ biến mất")
    void rescheduleHonoursHolds() throws Exception {
        long appointmentId = json(book(anna.token(), slot.getId(), null).andExpect(status().isCreated()))
                .get("id").asLong();
        AppointmentSlot target = data.futureSlot(doctor.doctorId());
        String url = "/api/v1/appointments/" + appointmentId + "/reschedule";

        holdOk(binh.token(), target.getId());
        mvc.perform(patch(url).header("Authorization", anna.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newSlotId\":%d}".formatted(target.getId()))).andExpect(status().isConflict());
        release(binh.token(), target.getId()).andExpect(status().isNoContent());

        long stale = holdOk(anna.token(), target.getId());
        expire(target.getId());
        mvc.perform(patch(url).header("Authorization", anna.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newSlotId\":%d,\"holdId\":%d}".formatted(target.getId(), stale)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("GONE"));
        assertEquals(slot.getId(), appointments.findById(appointmentId).orElseThrow().getSlot().getId());
        assertEquals("BOOKED", slots.findById(slot.getId()).orElseThrow().getStatus());
        assertEquals("AVAILABLE", slots.findById(target.getId()).orElseThrow().getStatus());

        long fresh = holdOk(anna.token(), target.getId());
        mvc.perform(patch(url).header("Authorization", anna.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newSlotId\":%d,\"holdId\":%d}".formatted(target.getId(), fresh)))
                .andExpect(status().isOk());

        assertEquals(target.getId(), appointments.findById(appointmentId).orElseThrow().getSlot().getId());
        assertEquals("AVAILABLE", slots.findById(slot.getId()).orElseThrow().getStatus());
        assertTrue(holds.findBySlotId(target.getId()).isEmpty());
    }

    // ---------- quầy lễ tân ----------

    @Test
    @DisplayName("Đặt hộ: bản giữ của lễ tân còn hạn 201; bệnh nhân đang giữ thì lễ tân 409; bản giữ của lễ tân hết hạn 410")
    void deskBookingHonoursHolds() throws Exception {
        long holdId = holdOk(desk.token(), slot.getId());
        bookAtDesk(desk.token(), binh.patientId(), slot.getId(), holdId).andExpect(status().isCreated());
        assertTrue(holds.findBySlotId(slot.getId()).isEmpty());

        AppointmentSlot heldByPatient = data.futureSlot(doctor.doctorId());
        holdOk(anna.token(), heldByPatient.getId());
        bookAtDesk(desk.token(), binh.patientId(), heldByPatient.getId(), null).andExpect(status().isConflict());

        AppointmentSlot expiring = data.futureSlot(doctor.doctorId());
        long stale = holdOk(desk.token(), expiring.getId());
        expire(expiring.getId());
        bookAtDesk(desk.token(), binh.patientId(), expiring.getId(), stale).andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value(GONE_MESSAGE));
        assertEquals(0, appointmentsOn(heldByPatient.getId()) + appointmentsOn(expiring.getId()));
    }

    @Test
    @DisplayName("Đổi lịch hộ kèm holdId của lễ tân: 200 và dòng giữ biến mất; holdId hết hạn 410, lịch cũ giữ nguyên")
    void deskRescheduleHonoursHolds() throws Exception {
        long appointmentId = json(bookAtDesk(desk.token(), binh.patientId(), slot.getId(), null)
                .andExpect(status().isCreated())).get("id").asLong();
        AppointmentSlot target = data.futureSlot(doctor.doctorId());
        String url = "/api/v1/reception/appointments/" + appointmentId + "/reschedule";

        long stale = holdOk(desk.token(), target.getId());
        expire(target.getId());
        mvc.perform(patch(url).header("Authorization", desk.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newSlotId\":%d,\"reason\":\"đổi giờ\",\"holdId\":%d}".formatted(target.getId(), stale)))
                .andExpect(status().isGone());
        assertEquals(slot.getId(), appointments.findById(appointmentId).orElseThrow().getSlot().getId());

        long fresh = holdOk(desk.token(), target.getId());
        mvc.perform(patch(url).header("Authorization", desk.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newSlotId\":%d,\"reason\":\"đổi giờ\",\"holdId\":%d}".formatted(target.getId(), fresh)))
                .andExpect(status().isOk());

        assertEquals(target.getId(), appointments.findById(appointmentId).orElseThrow().getSlot().getId());
        assertTrue(holds.findBySlotId(target.getId()).isEmpty());
    }

    // ---------- tác vụ dọn ----------

    @Test
    @DisplayName("AC-S1.6 Dọn: xóa dòng đã hết hạn, không đụng dòng còn hạn")
    void cleanupRemovesOnlyExpiredHolds() throws Exception {
        AppointmentSlot live = data.futureSlot(doctor.doctorId());
        long expiredId = holdOk(anna.token(), slot.getId());
        long liveId = holdOk(binh.token(), live.getId());
        expire(slot.getId());

        int removed = slotHoldService.removeExpired(LocalDateTime.now());

        assertTrue(removed >= 1);
        assertTrue(holds.findById(expiredId).isEmpty());
        assertTrue(holds.findById(liveId).isPresent());
        assertEquals(0, slotHoldService.removeExpired(LocalDateTime.now().minusHours(1)),
                "mốc thời gian trước mọi hạn thì không xóa gì");
        assertTrue(holds.findById(liveId).isPresent());
    }
}
