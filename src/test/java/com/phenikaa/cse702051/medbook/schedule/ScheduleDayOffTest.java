package com.phenikaa.cse702051.medbook.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.repository.DoctorDayOffRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-03.6, 03.8: ngày nghỉ của bác sĩ. Đăng ký nghỉ gỡ giờ trống của ngày đó nhưng không bao giờ làm mất lịch đã
 * đặt; xóa ngày nghỉ sinh lại giờ trống theo các ca đang có.
 */
class ScheduleDayOffTest extends AbstractScheduleTest {

    private static final String DAYS_OFF = URL + "/days-off";

    @Autowired
    private DoctorDayOffRepository dayOffs;

    private ResultActions addDayOff(String token, Object date, String reason) throws Exception {
        String body = "{\"date\":%s%s}".formatted(date == null ? "null" : "\"" + date + "\"",
                reason == null ? "" : ",\"reason\":\"" + reason + "\"");
        return mvc.perform(post(DAYS_OFF).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long addDayOffOk(String token, LocalDate date) throws Exception {
        return json(addDayOff(token, date, null).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    @DisplayName("AC-03.6 Đăng ký nghỉ: 201, gỡ mọi giờ trống của ngày đó, ca làm việc vẫn còn; có trong danh sách ngày nghỉ")
    void dayOffRemovesFreeSlotsButKeepsShifts() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();
        long morning = createOk(doctor.token(), day, "08:00", "10:00", 30, "[]");
        long afternoon = createOk(doctor.token(), day, "13:00", "14:00", 30, "[]");
        LocalDate otherDay = freshDay();
        createOk(doctor.token(), otherDay, "08:00", "09:00", 30, "[]");

        long id = json(addDayOff(doctor.token(), day, "  Đi hội thảo ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(day.toString()))
                .andExpect(jsonPath("$.reason").value("Đi hội thảo"))).get("id").asLong();

        assertTrue(publicSlotStarts(doctor.doctorId(), day).isEmpty());
        assertTrue(slots.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctor.doctorId(), day).isEmpty(),
                "slot chưa từng có lịch hẹn phải được xóa hẳn");
        assertEquals(0, getSchedule(doctor.token(), morning).get("totalSlots").asInt());
        assertEquals(30, getSchedule(doctor.token(), morning).get("slotMinutes").asInt());
        assertEquals(0, getSchedule(doctor.token(), afternoon).get("totalSlots").asInt());
        assertEquals(2, publicSlotStarts(doctor.doctorId(), otherDay).size(), "ngày khác không bị ảnh hưởng");

        mvc.perform(get(DAYS_OFF).param("from", day.toString()).param("to", day.toString())
                .header("Authorization", doctor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].date").value(day.toString()));
        assertEquals(1, countAudits(AuditActions.ENTITY_DOCTOR_DAY_OFFS, id, AuditActions.DAY_OFF_CREATE,
                doctor.userId()));
    }

    @Test
    @DisplayName("AC-03.6 Ngày đã có lịch hẹn được đặt: 409 kèm số lượng, không gỡ slot nào và không lưu ngày nghỉ")
    void dayWithBookedAppointmentCannotBecomeDayOff() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        IsolatedPatient patient = data.isolatedPatient("off" + unique());
        LocalDate day = freshDay();
        long scheduleId = createOk(doctor.token(), day, "08:00", "10:00", 30, "[]");
        bookFirstSlot(patient.token(), doctor.doctorId(), day);

        String message = json(addDayOff(doctor.token(), day, null).andExpect(status().isConflict()))
                .get("message").asText();
        assertTrue(message.contains("1 lịch hẹn"), message);

        assertFalse(dayOffs.existsByDoctorIdAndOffDate(doctor.doctorId(), day));
        assertEquals(3, publicSlotStarts(doctor.doctorId(), day).size());
        assertEquals(4, getSchedule(doctor.token(), scheduleId).get("totalSlots").asInt());
        assertEquals(1, getSchedule(doctor.token(), scheduleId).get("bookedSlots").asInt());
    }

    @Test
    @DisplayName("AC-03.6 Xóa ngày nghỉ: 204 và sinh lại đúng các giờ trống (vẫn bỏ giờ nghỉ trong ca); xóa lần nữa 404")
    void removingDayOffRegeneratesSlots() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();
        createOk(doctor.token(), day, "08:00", "10:00", 30, "[{\"startTime\":\"09:00\",\"endTime\":\"09:30\"}]");
        List<String> before = publicSlotStarts(doctor.doctorId(), day);
        assertEquals(List.of("08:00:00", "08:30:00", "09:30:00"), before);
        long id = addDayOffOk(doctor.token(), day);
        assertTrue(publicSlotStarts(doctor.doctorId(), day).isEmpty());

        mvc.perform(delete(DAYS_OFF + "/" + id).header("Authorization", doctor.token()))
                .andExpect(status().isNoContent());

        assertEquals(before, publicSlotStarts(doctor.doctorId(), day));
        assertEquals(3, slots.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctor.doctorId(), day).size());
        assertEquals(1, countAudits(AuditActions.ENTITY_DOCTOR_DAY_OFFS, id, AuditActions.DAY_OFF_DELETE,
                doctor.userId()));
        mvc.perform(delete(DAYS_OFF + "/" + id).header("Authorization", doctor.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-03.6 Slot từng có lịch đã hủy được giữ làm lịch sử khi nghỉ; hết nghỉ thì có slot mới đặt lại được")
    void cancelledHistoryIsKeptAcrossDayOff() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        IsolatedPatient patient = data.isolatedPatient("off" + unique());
        LocalDate day = freshDay();
        createOk(doctor.token(), day, "08:00", "09:00", 30, "[]");
        long appointmentId = bookFirstSlot(patient.token(), doctor.doctorId(), day);
        mvc.perform(patch("/api/v1/appointments/%d/cancel".formatted(appointmentId))
                .header("Authorization", patient.token()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        long id = addDayOffOk(doctor.token(), day); // lịch đã hủy không còn giữ chỗ nên được nghỉ
        List<AppointmentSlot> kept = slots.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctor.doctorId(), day);
        assertEquals(1, kept.size());
        assertEquals("CANCELLED", kept.get(0).getStatus());
        assertFalse(kept.get(0).getIsAvailable());

        mvc.perform(delete(DAYS_OFF + "/" + id).header("Authorization", doctor.token()))
                .andExpect(status().isNoContent());
        assertEquals(List.of("08:00:00", "08:30:00"), publicSlotStarts(doctor.doctorId(), day));
        bookFirstSlot(patient.token(), doctor.doctorId(), day);
    }

    @Test
    @DisplayName("AC-03.6 Ngày nghỉ ở quá khứ hoặc thiếu ngày 400; trùng ngày 422; tạo ca vào ngày nghỉ 422")
    void invalidDaysOffAndShiftsOnDaysOffAreRejected() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();

        addDayOff(doctor.token(), LocalDate.now().minusDays(1), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.date").isNotEmpty());
        addDayOff(doctor.token(), null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.date").isNotEmpty());
        addDayOff(doctor.token(), day, "x".repeat(256))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.reason").isNotEmpty());

        addDayOffOk(doctor.token(), day);
        addDayOff(doctor.token(), day, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"));
        assertEquals(1, dayOffs.findByDoctorIdAndOffDateBetweenOrderByOffDateAsc(doctor.doctorId(), day, day).size());

        createSchedule(doctor.token(), day, "08:00", "09:00", 30, "[]")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"));
        assertTrue(schedules.findByDoctorIdAndWorkDate(doctor.doctorId(), day).isEmpty());

        // hôm nay vẫn đăng ký nghỉ được (chưa phải ngày đã qua)
        addDayOff(doctor.token(), LocalDate.now(), null).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-03.8 Ngày nghỉ chỉ của mình: bác sĩ khác không thấy và không xóa được (403); bệnh nhân và Admin 403")
    void daysOffAreScopedToTheOwningDoctor() throws Exception {
        IsolatedDoctor a = newDoctor();
        IsolatedDoctor b = newDoctor();
        LocalDate day = freshDay();
        long id = addDayOffOk(a.token(), day);

        mvc.perform(get(DAYS_OFF).param("from", day.toString()).header("Authorization", b.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete(DAYS_OFF + "/" + id).header("Authorization", b.token()))
                .andExpect(status().isForbidden());
        addDayOff(b.token(), day, null).andExpect(status().isCreated()); // cùng ngày nhưng là bác sĩ khác

        for (String token : new String[] { patientToken(), adminToken() }) {
            mvc.perform(get(DAYS_OFF).header("Authorization", token)).andExpect(status().isForbidden());
            addDayOff(token, day.plusDays(1), null).andExpect(status().isForbidden());
            mvc.perform(delete(DAYS_OFF + "/" + id).header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(DAYS_OFF)).andExpect(status().isUnauthorized());

        assertTrue(dayOffs.existsById(id));
        mvc.perform(get(DAYS_OFF).param("from", day.toString()).param("to", day.plusDays(400).toString())
                .header("Authorization", a.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.to").isNotEmpty());
    }
}
