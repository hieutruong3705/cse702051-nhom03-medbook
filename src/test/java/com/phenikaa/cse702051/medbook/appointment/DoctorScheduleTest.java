package com.phenikaa.cse702051.medbook.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;

/**
 * YCCN-14/15: bác sĩ tự tạo ca làm việc → hệ thống sinh slot → bệnh nhân thấy giờ trống. Mỗi test dùng một
 * ngày tương lai riêng và bác sĩ dựng riêng nên không ảnh hưởng nhau (DB dùng chung giữa các test).
 */
class DoctorScheduleTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long SERVICE_ID = 1L; // seed: DV01 Khám tổng quát, ACTIVE
    private static final AtomicInteger DAY = new AtomicInteger();

    @Autowired
    private AppointmentSlotRepository slots;

    private IsolatedDoctor doctorA() {
        return data.isolatedDoctor("sched_a");
    }

    private IsolatedDoctor doctorB() {
        return data.isolatedDoctor("sched_b");
    }

    private static LocalDate freshDay() {
        return LocalDate.now().plusDays(300L + DAY.incrementAndGet());
    }

    // ---------- helpers ----------

    private ResultActions createSchedule(String token, LocalDate date, String start, String end, Object minutes,
            String breaksJson) throws Exception {
        String body = "{\"workDate\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"slotMinutes\":%s,\"breaks\":%s}"
                .formatted(date, start, end, minutes, breaksJson);
        return mvc.perform(post("/api/v1/doctor-schedules").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode createOk(String token, LocalDate date, String start, String end, int minutes, String breaksJson)
            throws Exception {
        MvcResult result = createSchedule(token, date, start, end, minutes, breaksJson)
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private List<String> publicSlotStarts(long doctorId, LocalDate date) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/doctors/%d/slots".formatted(doctorId)).param("date", date.toString()))
                .andExpect(status().isOk()).andReturn();
        List<String> starts = new ArrayList<>();
        JSON.readTree(result.getResponse().getContentAsString()).forEach(node -> starts.add(node.get("startTime").asText()));
        return starts;
    }

    private JsonNode slotsOfDay(String token, LocalDate date) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/doctor-schedules/slots").param("date", date.toString())
                .header("Authorization", token)).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private long bookFirstSlot(LocalDate date, long doctorId) throws Exception {
        long slotId = slots.findByDoctorIdAndSlotDateAndIsAvailableTrue(doctorId, date).stream()
                .min((a, b) -> a.getStartTime().compareTo(b.getStartTime())).orElseThrow().getId();
        MvcResult result = mvc.perform(post("/api/v1/appointments").header("Authorization", patientToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":%d}".formatted(slotId, SERVICE_ID)))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ---------- tạo ca và sinh slot ----------

    @Test
    @DisplayName("Tạo ca: 201, sinh slot đều nhau, bỏ slot chạm giờ nghỉ; bệnh nhân/khách thấy đúng các giờ trống")
    void createsScheduleAndGeneratesSlots() throws Exception {
        IsolatedDoctor doctor = doctorA();
        LocalDate day = freshDay();

        createSchedule(doctor.token(), day, "08:00", "11:00", 30,
                "[{\"startTime\":\"09:00\",\"endTime\":\"09:30\",\"reason\":\"Họp khoa\"}]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doctorId").value(doctor.doctorId()))
                .andExpect(jsonPath("$.workDate").value(day.toString()))
                .andExpect(jsonPath("$.slotMinutes").value(30))
                .andExpect(jsonPath("$.totalSlots").value(5))
                .andExpect(jsonPath("$.bookedSlots").value(0))
                .andExpect(jsonPath("$.breaks.length()").value(1))
                .andExpect(jsonPath("$.breaks[0].reason").value("Họp khoa"));

        // khách (không đăng nhập) xem được giờ trống của bác sĩ; slot 09:00-09:30 nằm trong giờ nghỉ nên không có
        assertEquals(List.of("08:00:00", "08:30:00", "09:30:00", "10:00:00", "10:30:00"),
                publicSlotStarts(doctor.doctorId(), day));

        // đường dẫn cũ cho bệnh nhân đã đăng nhập trả cùng kết quả dạng DTO (không lộ entity)
        mvc.perform(get("/api/v1/appointment-slots/available").param("doctorId", String.valueOf(doctor.doctorId()))
                .param("date", day.toString()).header("Authorization", patientToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].doctorId").value(doctor.doctorId()))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].version").doesNotExist())
                .andExpect(jsonPath("$[0].doctor").doesNotExist());
    }

    @Test
    @DisplayName("Tạo ca: dữ liệu sai → 400 (ngày đã qua, giờ ngược, ca ngắn hơn một slot, nghỉ ngoài ca, thiếu trường)")
    void rejectsInvalidSchedules() throws Exception {
        String token = doctorA().token();
        LocalDate day = freshDay();

        createSchedule(token, LocalDate.now().minusDays(1), "08:00", "11:00", 30, "[]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.workDate").exists());
        createSchedule(token, day, "11:00", "08:00", 30, "[]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.endTime").exists());
        createSchedule(token, day, "08:00", "08:20", 30, "[]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.slotMinutes").exists());
        createSchedule(token, day, "08:00", "11:00", 30, "[{\"startTime\":\"10:30\",\"endTime\":\"11:30\"}]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.breaks").exists());
        createSchedule(token, day, "08:00", "11:00", 1, "[]").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/doctor-schedules").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // không có ca nào được tạo từ các yêu cầu sai
        assertTrue(publicSlotStarts(doctorA().doctorId(), day).isEmpty());
    }

    @Test
    @DisplayName("Ca chồng giờ với ca đã có trong ngày → 422; ca liền kề thì hợp lệ")
    void overlappingScheduleIsUnprocessable() throws Exception {
        String token = doctorA().token();
        LocalDate day = freshDay();
        createOk(token, day, "08:00", "11:00", 30, "[]");

        createSchedule(token, day, "10:30", "12:00", 30, "[]")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"));
        createSchedule(token, day, "07:00", "08:30", 30, "[]").andExpect(status().isUnprocessableContent());

        createSchedule(token, day, "11:00", "12:00", 30, "[]").andExpect(status().isCreated());
        assertEquals(8, publicSlotStarts(doctorA().doctorId(), day).size());
    }

    // ---------- quyền ----------

    @Test
    @DisplayName("Chỉ bác sĩ được quản lý ca: bệnh nhân/Admin → 403, chưa đăng nhập → 401")
    void onlyDoctorsManageSchedules() throws Exception {
        LocalDate day = freshDay();
        createSchedule(patientToken(), day, "08:00", "09:00", 30, "[]").andExpect(status().isForbidden());
        createSchedule(adminToken(), day, "08:00", "09:00", 30, "[]").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/doctor-schedules").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/doctor-schedules").header("Authorization", patientToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Bác sĩ khác không xem/sửa/xóa/thêm giờ nghỉ vào ca của người khác → 403; danh sách chỉ có ca của mình")
    void doctorsCannotTouchEachOthersSchedules() throws Exception {
        IsolatedDoctor a = doctorA();
        IsolatedDoctor b = doctorB();
        LocalDate day = freshDay();
        long scheduleId = createOk(a.token(), day, "08:00", "10:00", 30, "[]").get("id").asLong();

        mvc.perform(get("/api/v1/doctor-schedules/" + scheduleId).header("Authorization", b.token()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/doctor-schedules/" + scheduleId).header("Authorization", b.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"endTime\":\"09:00\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/doctor-schedules/" + scheduleId).header("Authorization", b.token()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/doctor-schedules/%d/breaks".formatted(scheduleId)).header("Authorization", b.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"startTime\":\"08:00\",\"endTime\":\"08:30\"}"))
                .andExpect(status().isForbidden());

        // ca của A còn nguyên
        assertEquals(4, publicSlotStarts(a.doctorId(), day).size());

        // b không thấy ca của a trong danh sách của mình
        mvc.perform(get("/api/v1/doctor-schedules").param("from", day.toString()).param("to", day.toString())
                .header("Authorization", b.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/doctor-schedules").param("from", day.toString()).param("to", day.toString())
                .header("Authorization", a.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(scheduleId))
                .andExpect(jsonPath("$.content[0].totalSlots").value(4));
    }

    // ---------- giờ trống ----------

    @Test
    @DisplayName("Giờ trống: slot đã đặt biến mất; slot đã qua trong hôm nay, ngày đã qua, bác sĩ không tồn tại xử lý đúng")
    void publicSlotsHideUnbookableOnes() throws Exception {
        IsolatedDoctor doctor = doctorA();
        LocalDate day = freshDay();
        createOk(doctor.token(), day, "08:00", "09:30", 30, "[]");
        assertEquals(3, publicSlotStarts(doctor.doctorId(), day).size());

        bookFirstSlot(day, doctor.doctorId());
        assertEquals(List.of("08:30:00", "09:00:00"), publicSlotStarts(doctor.doctorId(), day));

        // slot hôm nay đã qua giờ bắt đầu không được trả về (00:00 luôn không sau "bây giờ")
        AppointmentSlot past = data.slot(doctor.doctorId(), LocalDate.now(), LocalTime.of(0, 0));
        assertFalse(publicSlotStarts(doctor.doctorId(), LocalDate.now()).contains("00:00:00"));
        assertTrue(slots.findById(past.getId()).isPresent());

        // ngày đã qua → rỗng; bác sĩ không tồn tại → 404
        assertTrue(publicSlotStarts(doctor.doctorId(), LocalDate.now().minusDays(1)).isEmpty());
        mvc.perform(get("/api/v1/doctors/999999/slots").param("date", day.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Bác sĩ xem slot của mình trong ngày kèm trạng thái trống/đã đặt")
    void doctorSeesOwnDaySlotsWithStatus() throws Exception {
        IsolatedDoctor doctor = doctorA();
        LocalDate day = freshDay();
        createOk(doctor.token(), day, "08:00", "09:30", 30, "[]");
        bookFirstSlot(day, doctor.doctorId());

        JsonNode daySlots = slotsOfDay(doctor.token(), day);
        assertEquals(3, daySlots.size());
        assertEquals("BOOKED", daySlots.get(0).get("status").asText());
        assertEquals("AVAILABLE", daySlots.get(1).get("status").asText());
        assertEquals("AVAILABLE", daySlots.get(2).get("status").asText());
    }

    // ---------- sửa / xóa ca ----------

    @Test
    @DisplayName("Sửa ca khi chưa có lịch đặt: sinh lại slot theo giờ mới, giữ độ dài slot")
    void updateRegeneratesSlots() throws Exception {
        IsolatedDoctor doctor = doctorA();
        LocalDate day = freshDay();
        long id = createOk(doctor.token(), day, "08:00", "10:00", 30, "[]").get("id").asLong();

        mvc.perform(put("/api/v1/doctor-schedules/" + id).header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"startTime\":\"09:00\",\"endTime\":\"11:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.slotMinutes").value(30))
                .andExpect(jsonPath("$.totalSlots").value(4));
        assertEquals(List.of("09:00:00", "09:30:00", "10:00:00", "10:30:00"), publicSlotStarts(doctor.doctorId(), day));

        // đổi độ dài slot
        mvc.perform(put("/api/v1/doctor-schedules/" + id).header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"slotMinutes\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotMinutes").value(60))
                .andExpect(jsonPath("$.totalSlots").value(2));
    }

    @Test
    @DisplayName("Có lịch đã đặt trong ca: sửa/xóa/thêm giờ nghỉ chạm slot đã đặt → 409 và ca giữ nguyên; hủy lịch rồi mới xóa được")
    void bookedAppointmentsBlockChangesUntilCancelled() throws Exception {
        IsolatedDoctor doctor = doctorA();
        LocalDate day = freshDay();
        long scheduleId = createOk(doctor.token(), day, "08:00", "10:00", 30, "[]").get("id").asLong();
        long appointmentId = bookFirstSlot(day, doctor.doctorId()); // slot 08:00-08:30

        mvc.perform(delete("/api/v1/doctor-schedules/" + scheduleId).header("Authorization", doctor.token()))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/doctor-schedules/" + scheduleId).header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"endTime\":\"09:00\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/doctor-schedules/%d/breaks".formatted(scheduleId))
                .header("Authorization", doctor.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\"08:00\",\"endTime\":\"08:30\"}"))
                .andExpect(status().isConflict());

        // ca và lịch hẹn vẫn nguyên vẹn sau các lần bị từ chối
        mvc.perform(get("/api/v1/doctor-schedules/" + scheduleId).header("Authorization", doctor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSlots").value(4))
                .andExpect(jsonPath("$.bookedSlots").value(1));

        // giờ nghỉ ở phần chưa đặt thì được
        mvc.perform(post("/api/v1/doctor-schedules/%d/breaks".formatted(scheduleId))
                .header("Authorization", doctor.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\"09:30\",\"endTime\":\"10:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalSlots").value(3));

        // hủy lịch rồi xóa ca: slot đã từng có lịch được gỡ khỏi lịch (không xóa cứng vì khóa ngoại)
        mvc.perform(patch("/api/v1/appointments/%d/cancel".formatted(appointmentId))
                .header("Authorization", patientToken()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/doctor-schedules/" + scheduleId).header("Authorization", doctor.token()))
                .andExpect(status().isNoContent());

        assertTrue(publicSlotStarts(doctor.doctorId(), day).isEmpty());
        assertTrue(slotsOfDay(doctor.token(), day).isEmpty());
        List<AppointmentSlot> left = slots.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctor.doctorId(), day);
        assertEquals(1, left.size());
        assertEquals("CANCELLED", left.get(0).getStatus());
        assertFalse(left.get(0).getIsAvailable());
        mvc.perform(get("/api/v1/doctor-schedules/" + scheduleId).header("Authorization", doctor.token()))
                .andExpect(status().isNotFound());
    }

    // ---------- giờ nghỉ ----------

    @Test
    @DisplayName("Thêm giờ nghỉ gỡ slot trống bị chạm; xóa giờ nghỉ sinh lại đúng các slot đó")
    void addAndRemoveBreak() throws Exception {
        IsolatedDoctor doctor = doctorA();
        LocalDate day = freshDay();
        long id = createOk(doctor.token(), day, "08:00", "10:00", 30, "[]").get("id").asLong();

        MvcResult added = mvc.perform(post("/api/v1/doctor-schedules/%d/breaks".formatted(id))
                .header("Authorization", doctor.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\"08:30\",\"endTime\":\"09:00\",\"reason\":\"Nghỉ giữa ca\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalSlots").value(3))
                .andReturn();
        long breakId = JSON.readTree(added.getResponse().getContentAsString()).get("breaks").get(0).get("id").asLong();
        assertEquals(List.of("08:00:00", "09:00:00", "09:30:00"), publicSlotStarts(doctor.doctorId(), day));

        mvc.perform(delete("/api/v1/doctor-schedules/%d/breaks/%d".formatted(id, breakId))
                .header("Authorization", doctor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSlots").value(4))
                .andExpect(jsonPath("$.breaks.length()").value(0));
        assertEquals(List.of("08:00:00", "08:30:00", "09:00:00", "09:30:00"), publicSlotStarts(doctor.doctorId(), day));

        // giờ nghỉ không tồn tại / ngoài ca
        mvc.perform(delete("/api/v1/doctor-schedules/%d/breaks/%d".formatted(id, breakId))
                .header("Authorization", doctor.token())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/doctor-schedules/%d/breaks".formatted(id)).header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"startTime\":\"07:00\",\"endTime\":\"08:00\"}"))
                .andExpect(status().isBadRequest());
    }
}
