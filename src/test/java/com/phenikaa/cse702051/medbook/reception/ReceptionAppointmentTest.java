package com.phenikaa.cse702051.medbook.reception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.NotificationRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedReceptionist;

/**
 * Lịch hẹn tại quầy lễ tân. Xem lịch toàn phòng khám; đặt, hủy, đổi lịch hộ bệnh nhân; nhật ký ghi người làm
 * hộ; chỉ lễ tân gọi được. Mỗi test dựng bác sĩ, bệnh nhân, khung giờ riêng.
 */
class ReceptionAppointmentTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "/api/v1/reception/appointments";
    private static final String PATIENTS = "/api/v1/reception/patients";

    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private AppointmentSlotRepository slots;
    @Autowired
    private PatientRepository patients;
    @Autowired
    private NotificationRepository notifications;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    private static String phone() {
        return "09" + String.format("%08d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000L));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Bệnh nhân không có tài khoản, tạo qua chính API của quầy. */
    private JsonNode walkIn(String marker) throws Exception {
        return json(mvc.perform(post(PATIENTS).header("Authorization", receptionistToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Vãng Lai %s\",\"dateOfBirth\":\"1985-02-10\",\"genderCode\":\"FEMALE\","
                        .formatted(marker) + "\"phone\":\"" + phone() + "\"}"))
                .andExpect(status().isCreated()));
    }

    private ResultActions book(String token, Object patientId, Object slotId, Object serviceId, String notes)
            throws Exception {
        StringBuilder body = new StringBuilder("{");
        if (patientId != null) {
            body.append("\"patientId\":").append(patientId).append(',');
        }
        if (slotId != null) {
            body.append("\"slotId\":").append(slotId).append(',');
        }
        if (serviceId != null) {
            body.append("\"serviceId\":").append(serviceId).append(',');
        }
        if (notes != null) {
            body.append("\"notes\":\"").append(notes).append("\",");
        }
        if (body.charAt(body.length() - 1) == ',') {
            body.setLength(body.length() - 1);
        }
        return mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(body.append('}').toString()));
    }

    private long booked(long patientId, long slotId) throws Exception {
        return json(book(receptionistToken(), patientId, slotId, 1, null).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private ResultActions cancel(String token, long id, String body) throws Exception {
        return mvc.perform(patch(URL + "/" + id + "/cancel").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions reschedule(String token, long id, String body) throws Exception {
        return mvc.perform(patch(URL + "/" + id + "/reschedule").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions list(String... params) throws Exception {
        var request = get(URL).header("Authorization", receptionistToken());
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    /** Khung giờ của bác sĩ bắt đầu sau {@code minutesFromNow} phút (âm = đã qua), dài tối đa 30 phút. */
    private AppointmentSlot slotAt(long doctorId, long minutesFromNow, boolean booked) {
        LocalDateTime start = LocalDateTime.now().plusMinutes(minutesFromNow).withSecond(0).withNano(0);
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = startTime.isAfter(LocalTime.of(23, 29)) ? LocalTime.of(23, 59, 59) : startTime.plusMinutes(30);
        AppointmentSlot template = data.futureSlot(doctorId);
        template.setSlotDate(start.toLocalDate());
        template.setStartTime(startTime);
        template.setEndTime(endTime);
        template.setIsAvailable(!booked);
        template.setStatus(booked ? "BOOKED" : "AVAILABLE");
        return slots.save(template);
    }

    /** Lịch BOOKED dựng thẳng vào CSDL trên một khung giờ cho trước (dùng cho khung giờ không đặt được qua API). */
    private Appointment bookedDirectly(AppointmentSlot slot, long patientId) {
        return appointments.save(Appointment.builder()
                .patientId(patientId)
                .doctor(slot.getDoctor())
                .slot(slot)
                .status(AppointmentStatus.BOOKED)
                .build());
    }

    // ---------- đặt hộ ----------

    @Test
    @DisplayName("Đặt hộ cho bệnh nhân có tài khoản: 201, khung giờ BOOKED, một bản nhật ký của lễ tân, bệnh nhân nhận thông báo")
    void booksForPatientWithAccount() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rab" + key);
        IsolatedPatient patient = data.isolatedPatient("rab" + key);
        IsolatedReceptionist receptionist = data.isolatedReceptionist("rab" + key);
        AppointmentSlot slot = data.futureSlot(doctor.doctorId());
        long unreadBefore = notifications.countByUserIdAndReadAtIsNull(patient.userId());

        ResultActions result = book(receptionist.token(), patient.patientId(), slot.getId(), 1, "ghi-chu-rieng-" + key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.patientId").value(patient.patientId()))
                .andExpect(jsonPath("$.patientName").isNotEmpty())
                .andExpect(jsonPath("$.patientCode").isNotEmpty())
                .andExpect(jsonPath("$.patientPhone").value("0900000002"))
                .andExpect(jsonPath("$.doctorId").value(doctor.doctorId()))
                .andExpect(jsonPath("$.serviceId").value(1))
                .andExpect(jsonPath("$.serviceName").isNotEmpty())
                .andExpect(jsonPath("$.slotId").value(slot.getId()))
                .andExpect(jsonPath("$.date").value(slot.getSlotDate().toString()))
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.cancelReason").doesNotExist());
        long id = json(result).get("id").asLong();
        result.andExpect(header().string("Location", URL + "/" + id));
        assertFalse(result.andReturn().getResponse().getContentAsString().contains("ghi-chu-rieng"),
                "ghi chú chỉ ghi một chiều, không trả lại cho lễ tân");

        assertEquals("BOOKED", slots.findById(slot.getId()).orElseThrow().getStatus());
        assertEquals("ghi-chu-rieng-" + key, appointments.findById(id).orElseThrow().getNotes());
        assertEquals(1, countAudits(AuditActions.ENTITY_APPOINTMENTS, id, AuditActions.APPOINTMENT_BOOK_ON_BEHALF,
                receptionist.userId()));
        String metadata = auditsOf(AuditActions.ENTITY_APPOINTMENTS, id).get(0).getMetadataJson();
        assertTrue(metadata.contains("patientId") && metadata.contains("slotId"), metadata);
        assertFalse(metadata.contains("ghi-chu-rieng"), "nhật ký không chứa ghi chú");
        assertEquals(unreadBefore + 1, notifications.countByUserIdAndReadAtIsNull(patient.userId()),
                "bệnh nhân có tài khoản nhận thông báo đặt lịch");

        // bệnh nhân thấy lịch do quầy đặt trong danh sách của mình
        mvc.perform(get("/api/v1/appointments/" + id).header("Authorization", patient.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"));
    }

    @Test
    @DisplayName("Đặt hộ cho bệnh nhân không có tài khoản: 201, không lỗi vì không có người nhận thông báo")
    void booksForWalkInPatient() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("raw" + key);
        JsonNode patient = walkIn(key);
        AppointmentSlot slot = data.futureSlot(doctor.doctorId());

        book(receptionistToken(), patient.get("id").asLong(), slot.getId(), 1, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientCode").value(patient.get("patientCode").asText()))
                .andExpect(jsonPath("$.status").value("BOOKED"));
        assertEquals(1, appointments.countActiveBySlotId(slot.getId()));
    }

    @Test
    @DisplayName("Đặt hộ: thiếu trường 400 theo trường; ghi chú quá dài 400; dịch vụ không tồn tại 400")
    void bookingValidatesInput() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rav" + key);
        long patientId = walkIn(key).get("id").asLong();
        AppointmentSlot slot = data.futureSlot(doctor.doctorId());

        book(receptionistToken(), null, slot.getId(), 1, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.patientId").isNotEmpty());
        book(receptionistToken(), patientId, null, 1, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.slotId").isNotEmpty());
        book(receptionistToken(), patientId, slot.getId(), null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.serviceId").isNotEmpty());
        book(receptionistToken(), patientId, slot.getId(), 1, "x".repeat(2001))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.notes").isNotEmpty());
        book(receptionistToken(), patientId, slot.getId(), 999_999, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.serviceId").isNotEmpty());

        assertEquals("AVAILABLE", slots.findById(slot.getId()).orElseThrow().getStatus(),
                "yêu cầu sai không được chiếm khung giờ");
    }

    @Test
    @DisplayName("Đặt hộ: bệnh nhân hoặc khung giờ không tồn tại 404; hồ sơ ngừng hoạt động 422; khung giờ đã đặt hoặc đã qua 409")
    void bookingRejectsUnusableTargets() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rar" + key);
        long patientId = walkIn(key).get("id").asLong();
        AppointmentSlot slot = data.futureSlot(doctor.doctorId());

        book(receptionistToken(), 999_999_999, slot.getId(), 1, null).andExpect(status().isNotFound());
        book(receptionistToken(), patientId, 999_999_999, 1, null).andExpect(status().isNotFound());

        long inactiveId = walkIn("nghi" + key).get("id").asLong();
        Patient inactive = patients.findById(inactiveId).orElseThrow();
        inactive.setStatus("INACTIVE");
        patients.save(inactive);
        book(receptionistToken(), inactiveId, slot.getId(), 1, null).andExpect(status().isUnprocessableEntity());

        AppointmentSlot past = slotAt(doctor.doctorId(), -90, false);
        book(receptionistToken(), patientId, past.getId(), 1, null).andExpect(status().isConflict());

        booked(patientId, slot.getId());
        long other = walkIn("khac" + key).get("id").asLong();
        book(receptionistToken(), other, slot.getId(), 1, null).andExpect(status().isConflict());
        assertEquals(1, appointments.countActiveBySlotId(slot.getId()));
    }

    // ---------- danh sách ----------

    @Test
    @DisplayName("Danh sách lọc theo ngày, bác sĩ, trạng thái, từ khóa bệnh nhân; có điện thoại, không có ghi chú hay lý do hủy")
    void listFiltersAndHidesNotes() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("ral" + key);
        IsolatedDoctor otherDoctor = data.isolatedDoctor("ralx" + key);
        JsonNode patientA = walkIn("anh" + key);
        JsonNode patientB = walkIn("binh" + key);
        AppointmentSlot slotA = data.futureSlot(doctor.doctorId());
        AppointmentSlot slotB = data.slot(doctor.doctorId(), slotA.getSlotDate(), LocalTime.of(10, 0));
        AppointmentSlot slotOther = data.slot(otherDoctor.doctorId(), slotA.getSlotDate(), LocalTime.of(8, 0));
        long idA = json(book(receptionistToken(), patientA.get("id").asLong(), slotA.getId(), 1, "bi-mat-" + key)
                .andExpect(status().isCreated())).get("id").asLong();
        long idB = booked(patientB.get("id").asLong(), slotB.getId());
        booked(patientA.get("id").asLong(), slotOther.getId());
        cancel(receptionistToken(), idB, "{\"reason\":\"ly-do-kin-" + key + "\"}").andExpect(status().isOk());
        String day = slotA.getSlotDate().toString();
        String doctorId = String.valueOf(doctor.doctorId());

        String all = list("from", day, "to", day, "doctorId", doctorId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(idA))
                .andExpect(jsonPath("$.content[0].patientPhone").value(patientA.get("phone").asText()))
                .andExpect(jsonPath("$.content[0].notes").doesNotExist())
                .andExpect(jsonPath("$.content[1].id").value(idB))
                .andExpect(jsonPath("$.content[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.content[1].cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.content[1].cancelReason").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertFalse(all.contains("bi-mat-") || all.contains("ly-do-kin-"), "không lộ ghi chú hay lý do hủy");

        list("from", day, "to", day).andExpect(jsonPath("$.totalElements").value(3));
        list("from", day, "to", day, "doctorId", doctorId, "status", "BOOKED")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idA));
        list("from", day, "to", day, "keyword", patientB.get("patientCode").asText().toLowerCase())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idB));
        list("from", day, "to", day, "keyword", patientA.get("phone").asText())
                .andExpect(jsonPath("$.totalElements").value(2));
        list("from", day, "to", day, "keyword", "ANH" + key)
                .andExpect(jsonPath("$.totalElements").value(2));
        list("from", day, "to", day, "keyword", "%").andExpect(jsonPath("$.totalElements").value(0));
        list("from", day, "to", day, "size", "1000").andExpect(jsonPath("$.size").value(100));
        // chỉ nêu một đầu: lấy đúng ngày đó
        list("from", day, "doctorId", doctorId).andExpect(jsonPath("$.totalElements").value(2));
        list("to", day, "doctorId", doctorId).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Bỏ trống ngày thì chỉ có lịch hôm nay; from sau to hoặc trạng thái lạ trả 400")
    void listDefaultsToTodayAndRejectsBadFilters() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rat" + key);
        long patientId = walkIn(key).get("id").asLong();
        AppointmentSlot today = data.slot(doctor.doctorId(), LocalDate.now(), LocalTime.of(0, 5));
        today.setIsAvailable(false);
        today.setStatus("BOOKED");
        long todayId = bookedDirectly(slots.save(today), patientId).getId();
        booked(patientId, data.futureSlot(doctor.doctorId()).getId());

        list("doctorId", String.valueOf(doctor.doctorId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(todayId))
                .andExpect(jsonPath("$.content[0].date").value(LocalDate.now().toString()));

        list("from", "2026-12-31", "to", "2026-01-01").andExpect(status().isBadRequest());
        list("status", "KHONG_CO").andExpect(status().isBadRequest());
        list("from", "31-12-2026").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Xem một lịch hẹn: 200 với đúng trường hành chính; không tồn tại 404")
    void getsOneAppointment() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rag" + key);
        long patientId = walkIn(key).get("id").asLong();
        long id = booked(patientId, data.futureSlot(doctor.doctorId()).getId());

        mvc.perform(get(URL + "/" + id).header("Authorization", receptionistToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andExpect(jsonPath("$.doctorName").isNotEmpty())
                .andExpect(jsonPath("$.notes").doesNotExist());
        mvc.perform(get(URL + "/999999999").header("Authorization", receptionistToken()))
                .andExpect(status().isNotFound());
    }

    // ---------- hủy hộ ----------

    @Test
    @DisplayName("Hủy hộ: lịch CANCELLED, lý do được lưu, khung giờ được nhả và đặt lại được, một bản nhật ký của lễ tân")
    void cancelsOnBehalfAndReleasesSlot() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rac" + key);
        IsolatedReceptionist receptionist = data.isolatedReceptionist("rac" + key);
        long patientId = walkIn(key).get("id").asLong();
        AppointmentSlot slot = data.futureSlot(doctor.doctorId());
        long id = booked(patientId, slot.getId());

        String response = cancel(receptionist.token(), id, "{\"reason\":\"  Bệnh nhân gọi báo bận " + key + "  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.cancelReason").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(key + "  ") || response.contains("báo bận"));

        assertEquals("Bệnh nhân gọi báo bận " + key, appointments.findById(id).orElseThrow().getCancelReason());
        assertEquals("AVAILABLE", slots.findById(slot.getId()).orElseThrow().getStatus());
        assertEquals(1, countAudits(AuditActions.ENTITY_APPOINTMENTS, id, AuditActions.APPOINTMENT_CANCEL_ON_BEHALF,
                receptionist.userId()));
        assertFalse(auditsOf(AuditActions.ENTITY_APPOINTMENTS, id).stream()
                .anyMatch(a -> a.getMetadataJson() != null && a.getMetadataJson().contains("báo bận")),
                "nhật ký không chứa lý do hủy");

        booked(walkIn("sau" + key).get("id").asLong(), slot.getId());
        // hủy lần hai: lịch không còn BOOKED
        cancel(receptionistToken(), id, "{\"reason\":\"lần hai\"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Trong 2 giờ trước giờ khám: bệnh nhân tự hủy bị 409, lễ tân hủy hộ được")
    void receptionistMayCancelInsideThePatientCutoff() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("ra2" + key);
        IsolatedPatient patient = data.isolatedPatient("ra2" + key);
        AppointmentSlot soon = slotAt(doctor.doctorId(), 45, true);
        long id = bookedDirectly(soon, patient.patientId()).getId();

        mvc.perform(patch("/api/v1/appointments/" + id + "/cancel").header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"tự hủy\"}"))
                .andExpect(status().isConflict());
        assertEquals(AppointmentStatus.BOOKED, appointments.findById(id).orElseThrow().getStatus());

        cancel(receptionistToken(), id, "{\"reason\":\"Bệnh nhân gọi tới quầy\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Hủy hộ: thiếu hoặc quá dài lý do 400; sai trạng thái 422; đã qua giờ bắt đầu 409; không tồn tại 404")
    void cancelRejectsInvalidRequests() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rax" + key);
        long patientId = walkIn(key).get("id").asLong();
        long id = booked(patientId, data.futureSlot(doctor.doctorId()).getId());

        cancel(receptionistToken(), id, "{}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.reason").isNotEmpty());
        cancel(receptionistToken(), id, "{\"reason\":\"   \"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.reason").isNotEmpty());
        cancel(receptionistToken(), id, "{\"reason\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.reason").isNotEmpty());
        assertEquals(AppointmentStatus.BOOKED, appointments.findById(id).orElseThrow().getStatus());

        for (AppointmentStatus status : List.of(AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED)) {
            long other = data.newAppointment(doctor.doctorId(), patientId, status).getId();
            cancel(receptionistToken(), other, "{\"reason\":\"thử\"}").andExpect(status().isUnprocessableEntity());
            assertEquals(status, appointments.findById(other).orElseThrow().getStatus());
        }

        long started = bookedDirectly(slotAt(doctor.doctorId(), -30, true), patientId).getId();
        cancel(receptionistToken(), started, "{\"reason\":\"đã quá giờ\"}").andExpect(status().isConflict());
        assertEquals(AppointmentStatus.BOOKED, appointments.findById(started).orElseThrow().getStatus());

        cancel(receptionistToken(), 999_999_999L, "{\"reason\":\"thử\"}").andExpect(status().isNotFound());
    }

    // ---------- đổi hộ ----------

    @Test
    @DisplayName("Đổi hộ: sang khung giờ mới, khung cũ được nhả, hai mốc nhắc lịch bị xóa, nhật ký ghi khung cũ và mới")
    void reschedulesOnBehalf() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rars" + key);
        IsolatedReceptionist receptionist = data.isolatedReceptionist("rars" + key);
        long patientId = walkIn(key).get("id").asLong();
        AppointmentSlot oldSlot = data.futureSlot(doctor.doctorId());
        AppointmentSlot newSlot = data.futureSlot(doctor.doctorId());
        long id = booked(patientId, oldSlot.getId());
        Appointment reminded = appointments.findById(id).orElseThrow();
        reminded.setReminder24hSentAt(LocalDateTime.now());
        reminded.setReminder2hSentAt(LocalDateTime.now());
        appointments.save(reminded);

        reschedule(receptionist.token(), id,
                "{\"newSlotId\":%d,\"reason\":\"ly-do-doi-%s\"}".formatted(newSlot.getId(), key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.slotId").value(newSlot.getId()))
                .andExpect(jsonPath("$.date").value(newSlot.getSlotDate().toString()));

        Appointment moved = appointments.findById(id).orElseThrow();
        assertNull(moved.getReminder24hSentAt());
        assertNull(moved.getReminder2hSentAt());
        assertEquals("AVAILABLE", slots.findById(oldSlot.getId()).orElseThrow().getStatus());
        assertEquals("BOOKED", slots.findById(newSlot.getId()).orElseThrow().getStatus());
        assertEquals(1, appointments.countActiveBySlotId(newSlot.getId()));
        assertEquals(0, appointments.countActiveBySlotId(oldSlot.getId()));

        assertEquals(1, countAudits(AuditActions.ENTITY_APPOINTMENTS, id,
                AuditActions.APPOINTMENT_RESCHEDULE_ON_BEHALF, receptionist.userId()));
        String metadata = auditsOf(AuditActions.ENTITY_APPOINTMENTS, id).stream()
                .filter(a -> a.getActionCode().equals(AuditActions.APPOINTMENT_RESCHEDULE_ON_BEHALF))
                .findFirst().orElseThrow().getMetadataJson();
        assertTrue(metadata.contains("oldSlotId") && metadata.contains("newSlotId"), metadata);
        assertFalse(metadata.contains("ly-do-doi"), "lý do đổi không vào nhật ký");
    }

    @Test
    @DisplayName("Đổi hộ thất bại thì lịch và khung giờ cũ giữ nguyên: trùng khung cũ 400, khung mới đã đặt 409, không tồn tại 404")
    void failedRescheduleKeepsTheOldAppointment() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rarf" + key);
        long patientId = walkIn(key).get("id").asLong();
        AppointmentSlot oldSlot = data.futureSlot(doctor.doctorId());
        AppointmentSlot taken = data.futureSlot(doctor.doctorId());
        long id = booked(patientId, oldSlot.getId());
        booked(walkIn("khac" + key).get("id").asLong(), taken.getId());

        reschedule(receptionistToken(), id, "{\"newSlotId\":%d,\"reason\":\"thử\"}".formatted(oldSlot.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.newSlotId").isNotEmpty());
        reschedule(receptionistToken(), id, "{\"newSlotId\":%d,\"reason\":\"thử\"}".formatted(taken.getId()))
                .andExpect(status().isConflict());
        reschedule(receptionistToken(), id, "{\"newSlotId\":999999999,\"reason\":\"thử\"}")
                .andExpect(status().isNotFound());
        reschedule(receptionistToken(), id, "{\"newSlotId\":%d}".formatted(data.futureSlot(doctor.doctorId()).getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.reason").isNotEmpty());
        reschedule(receptionistToken(), id, "{\"reason\":\"thiếu khung giờ\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.newSlotId").isNotEmpty());

        Appointment unchanged = appointments.findById(id).orElseThrow();
        assertEquals(AppointmentStatus.BOOKED, unchanged.getStatus());
        assertEquals(oldSlot.getId(), unchanged.getSlot().getId());
        assertEquals("BOOKED", slots.findById(oldSlot.getId()).orElseThrow().getStatus());
        assertEquals(1, appointments.countActiveBySlotId(taken.getId()));
        assertEquals(0, countAudits(AuditActions.ENTITY_APPOINTMENTS, id,
                AuditActions.APPOINTMENT_RESCHEDULE_ON_BEHALF, RECEPTIONIST_USER_ID));
    }

    @Test
    @DisplayName("Đổi hộ: sai trạng thái 422; lịch đã qua giờ bắt đầu 409")
    void rescheduleRequiresABookedFutureAppointment() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rarq" + key);
        long patientId = walkIn(key).get("id").asLong();
        AppointmentSlot target = data.futureSlot(doctor.doctorId());
        String body = "{\"newSlotId\":%d,\"reason\":\"thử\"}".formatted(target.getId());

        long completed = data.newAppointment(doctor.doctorId(), patientId, AppointmentStatus.COMPLETED).getId();
        reschedule(receptionistToken(), completed, body).andExpect(status().isUnprocessableEntity());
        long started = bookedDirectly(slotAt(doctor.doctorId(), -30, true), patientId).getId();
        reschedule(receptionistToken(), started, body).andExpect(status().isConflict());

        assertEquals("AVAILABLE", slots.findById(target.getId()).orElseThrow().getStatus());
        assertNotNull(appointments.findById(started).orElseThrow().getSlot());
    }

    // ---------- quyền ----------

    @Test
    @DisplayName("Khách 401; bệnh nhân, bác sĩ, quản trị viên 403 ở cả năm điểm cuối")
    void onlyReceptionistsMayUseTheEndpoints() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rap" + key);
        IsolatedPatient patient = data.isolatedPatient("rap" + key);
        AppointmentSlot slot = data.futureSlot(doctor.doctorId());
        AppointmentSlot free = data.futureSlot(doctor.doctorId());
        long id = booked(patient.patientId(), slot.getId());
        String cancelBody = "{\"reason\":\"thử\"}";
        String moveBody = "{\"newSlotId\":%d,\"reason\":\"thử\"}".formatted(free.getId());

        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(URL + "/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON).content(cancelBody))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(URL + "/" + id + "/reschedule").contentType(MediaType.APPLICATION_JSON).content(moveBody))
                .andExpect(status().isUnauthorized());

        // kể cả bệnh nhân chủ lịch và bác sĩ phụ trách cũng không dùng được đường của quầy
        for (String token : List.of(patient.token(), doctor.token(), adminToken())) {
            mvc.perform(get(URL).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get(URL + "/" + id).header("Authorization", token)).andExpect(status().isForbidden());
            book(token, patient.patientId(), free.getId(), 1, null).andExpect(status().isForbidden());
            cancel(token, id, cancelBody).andExpect(status().isForbidden());
            reschedule(token, id, moveBody).andExpect(status().isForbidden());
        }
        assertEquals(AppointmentStatus.BOOKED, appointments.findById(id).orElseThrow().getStatus());
        assertEquals("AVAILABLE", slots.findById(free.getId()).orElseThrow().getStatus());
    }
}
