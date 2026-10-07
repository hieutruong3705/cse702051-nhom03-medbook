package com.phenikaa.cse702051.medbook.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.DoctorSchedule;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.service.DoctorScheduleService;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-03.5, 03.7, 03.11: {@code rebuildSlots} là đường duy nhất sinh và gỡ slot. Gọi lặp lại không đổi gì, tự sửa
 * slot thừa/thiếu, không sinh slot đã qua giờ trong hôm nay; mỗi thao tác trên ca ghi đúng một bản audit.
 */
class ScheduleRebuildTest extends AbstractScheduleTest {

    @Autowired
    private DoctorScheduleService scheduleService;
    @Autowired
    private DoctorRepository doctors;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private int rebuild(long scheduleId) {
        return new TransactionTemplate(transactionManager).execute(
                tx -> scheduleService.rebuildSlots(schedules.findById(scheduleId).orElseThrow()));
    }

    private List<AppointmentSlot> daySlots(long doctorId, LocalDate day) {
        return slots.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctorId, day);
    }

    private static List<Long> ids(List<AppointmentSlot> slots) {
        return slots.stream().map(AppointmentSlot::getId).toList();
    }

    @Test
    @DisplayName("AC-03.7 rebuildSlots chạy hai lần liên tiếp: không tạo trùng, không xóa, các slot giữ nguyên mã")
    void rebuildIsIdempotent() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();
        long id = createOk(doctor.token(), day, "08:00", "11:00", 30,
                "[{\"startTime\":\"09:00\",\"endTime\":\"09:30\"}]");
        List<Long> before = ids(daySlots(doctor.doctorId(), day));
        assertEquals(5, before.size());

        assertEquals(5, rebuild(id));
        assertEquals(5, rebuild(id));

        assertEquals(before, ids(daySlots(doctor.doctorId(), day)));
        assertEquals(List.of("08:00:00", "08:30:00", "09:30:00", "10:00:00", "10:30:00"),
                publicSlotStarts(doctor.doctorId(), day));
    }

    @Test
    @DisplayName("AC-03.7 rebuildSlots tự sửa: thêm lại slot bị thiếu, gỡ slot lệch lưới, giữ slot đúng")
    void rebuildRepairsMissingAndStraySlots() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();
        long id = createOk(doctor.token(), day, "08:00", "10:00", 30, "[]");
        List<AppointmentSlot> original = daySlots(doctor.doctorId(), day);
        slots.delete(original.get(1)); // mất slot 08:30
        AppointmentSlot stray = data.slot(doctor.doctorId(), day, LocalTime.of(9, 10)); // 09:10–09:40 lệch lưới

        assertEquals(4, rebuild(id));

        List<AppointmentSlot> repaired = daySlots(doctor.doctorId(), day);
        assertEquals(List.of(LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30)),
                repaired.stream().map(AppointmentSlot::getStartTime).toList());
        assertTrue(slots.findById(stray.getId()).isEmpty());
        assertEquals(original.get(0).getId(), repaired.get(0).getId(), "slot đúng lưới không bị tạo lại");
        assertEquals(4, rebuild(id));
        assertEquals(ids(repaired), ids(daySlots(doctor.doctorId(), day)));
    }

    @Test
    @DisplayName("AC-03.5 Thêm rồi xóa giờ nghỉ nhiều lần: số slot luôn đúng, không bao giờ có hai slot cùng giờ")
    void repeatedBreakChangesNeverDuplicateSlots() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();
        long id = createOk(doctor.token(), day, "08:00", "10:00", 30, "[]");

        for (int round = 0; round < 3; round++) {
            JsonNode added = json(mvc.perform(post(URL + "/" + id + "/breaks").header("Authorization", doctor.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"startTime\":\"08:30\",\"endTime\":\"09:30\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.totalSlots").value(2)));
            long breakId = added.get("breaks").get(0).get("id").asLong();
            mvc.perform(delete(URL + "/" + id + "/breaks/" + breakId).header("Authorization", doctor.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSlots").value(4));
            rebuild(id);
        }

        List<LocalTime> starts = daySlots(doctor.doctorId(), day).stream().map(AppointmentSlot::getStartTime).toList();
        assertEquals(List.of(LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30)), starts);
    }

    @Test
    @DisplayName("AC-03.7 Ca tạo cho hôm nay không sinh slot đã qua giờ bắt đầu; dựng lại cũng không sinh thêm")
    void todayShiftSkipsSlotsThatAlreadyStarted() throws Exception {
        LocalTime now = LocalTime.now();
        assumeTrue(now.getHour() >= 1 && now.getHour() <= 21 && now.getMinute() < 58,
                "cần còn ít nhất một giờ tròn phía trước trong hôm nay");
        IsolatedDoctor doctor = newDoctor();
        LocalDate today = LocalDate.now();
        int hour = now.getHour();

        // ba ô 60 phút: [h-1, h) và [h, h+1) đã bắt đầu, chỉ [h+1, h+2) còn đặt được
        long id = json(createSchedule(doctor.token(), today, "%02d:00".formatted(hour - 1),
                "%02d:00".formatted(hour + 2), 60, "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotMinutes").value(60))
                .andExpect(jsonPath("$.totalSlots").value(1))).get("id").asLong();

        List<AppointmentSlot> created = daySlots(doctor.doctorId(), today);
        assertEquals(1, created.size());
        assertEquals(LocalTime.of(hour + 1, 0), created.get(0).getStartTime());
        assertEquals(1, rebuild(id));
        assertEquals(ids(created), ids(daySlots(doctor.doctorId(), today)));
    }

    @Test
    @DisplayName("AC-03.7 Ca tạo trước khi có cột slot_minutes: độ dài slot được suy ra rồi ghi lại ở lần thay đổi kế tiếp")
    void legacyScheduleWithoutSlotMinutesIsBackfilled() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();
        long id = schedules.save(DoctorSchedule.builder()
                .doctor(doctors.findById(doctor.doctorId()).orElseThrow())
                .workDate(day)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .build()).getId();
        for (int i = 0; i < 4; i++) {
            data.slot(doctor.doctorId(), day, LocalTime.of(8, 0).plusMinutes(30L * i));
        }
        assertNull(schedules.findById(id).orElseThrow().getSlotMinutes());
        assertEquals(30, getSchedule(doctor.token(), id).get("slotMinutes").asInt());

        mvc.perform(post(URL + "/" + id + "/breaks").header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"startTime\":\"09:00\",\"endTime\":\"09:30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotMinutes").value(30))
                .andExpect(jsonPath("$.totalSlots").value(3));

        assertEquals(30, schedules.findById(id).orElseThrow().getSlotMinutes());
        assertEquals(List.of("08:00:00", "08:30:00", "09:30:00"), publicSlotStarts(doctor.doctorId(), day));
    }

    @Test
    @DisplayName("AC-03.11 Tạo, sửa, xóa ca mỗi lần ghi đúng một bản audit kèm số slot; yêu cầu bị từ chối không ghi gì")
    void scheduleChangesAreAudited() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();
        long id = createOk(doctor.token(), day, "08:00", "10:00", 30, "[]");
        String type = AuditActions.ENTITY_DOCTOR_SCHEDULES;
        assertEquals(1, countAudits(type, id, AuditActions.SCHEDULE_CREATE, doctor.userId()));
        String metadata = auditsOf(type, id).getLast().getMetadataJson().replace("\\", "").replace(" ", "");
        assertTrue(metadata.contains("\"slots\":4"), metadata);

        // bị từ chối: chồng ca (422) và dữ liệu sai (400)
        createSchedule(doctor.token(), day, "09:00", "11:00", 30, "[]").andExpect(status().isUnprocessableContent());
        mvc.perform(put(URL + "/" + id).header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"slotMinutes\":7}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, auditsOf(type, id).size());

        mvc.perform(put(URL + "/" + id).header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"endTime\":\"11:00\"}"))
                .andExpect(status().isOk());
        assertEquals(1, countAudits(type, id, AuditActions.SCHEDULE_UPDATE, doctor.userId()));
        metadata = auditsOf(type, id).getLast().getMetadataJson().replace("\\", "").replace(" ", "");
        assertTrue(metadata.contains("\"slots\":6"), metadata);

        mvc.perform(delete(URL + "/" + id).header("Authorization", doctor.token()))
                .andExpect(status().isNoContent());
        assertEquals(1, countAudits(type, id, AuditActions.SCHEDULE_DELETE, doctor.userId()));
        assertEquals(3, auditsOf(type, id).size());
    }

    @Test
    @DisplayName("GET /doctor-schedules/{id}/slots: slot của ca kèm mã lịch hẹn đang giữ chỗ, không có thông tin bệnh nhân")
    void slotsOfScheduleShowAppointmentIds() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        IsolatedPatient patient = data.isolatedPatient("slt" + unique());
        LocalDate day = freshDay();
        long id = createOk(doctor.token(), day, "08:00", "09:30", 30, "[]");
        createOk(doctor.token(), day, "13:00", "14:00", 30, "[]"); // ca khác cùng ngày không lẫn vào
        long appointmentId = bookFirstSlot(patient.token(), doctor.doctorId(), day);

        String raw = mvc.perform(get(URL + "/" + id + "/slots").header("Authorization", doctor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("08:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("08:30:00"))
                .andExpect(jsonPath("$[0].status").value("BOOKED"))
                .andExpect(jsonPath("$[0].appointmentId").value(appointmentId))
                .andExpect(jsonPath("$[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].appointmentId").doesNotExist())
                .andExpect(jsonPath("$[0].patientId").doesNotExist())
                .andExpect(jsonPath("$[0].doctor").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertTrue(!raw.contains("patient") && !raw.contains("version"), raw);

        mvc.perform(get(URL + "/" + id + "/slots").header("Authorization", newDoctor().token()))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL + "/999999/slots").header("Authorization", doctor.token()))
                .andExpect(status().isNotFound());
    }
}
