package com.phenikaa.cse702051.medbook.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;

/** AC-03.2 (phần còn lại): giới hạn nghiệp vụ của ca làm việc. Vượt giới hạn → 400 đúng trường, không tạo gì. */
class ScheduleLimitsTest extends AbstractScheduleTest {

    /** {@code count} giờ nghỉ 5 phút liên tiếp, cách nhau 5 phút, bắt đầu từ 08:00. */
    private static String breaks(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "{\"startTime\":\"%02d:%02d\",\"endTime\":\"%02d:%02d\"}".formatted(
                        8 + (i * 10) / 60, (i * 10) % 60, 8 + (i * 10 + 5) / 60, (i * 10 + 5) % 60))
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Test
    @DisplayName("AC-03.2 Số phút mỗi slot phải từ 5 đến 120 và chia hết cho 5; ca dài tối đa 12 giờ")
    void slotMinutesAndShiftLengthAreLimited() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();

        for (int minutes : new int[] { 7, 31, 125, 240 }) {
            createSchedule(doctor.token(), day, "08:00", "17:00", minutes, "[]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.slotMinutes").isNotEmpty());
        }
        createSchedule(doctor.token(), day, "07:00", "19:30", 30, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.endTime").isNotEmpty());
        assertTrue(publicSlotStarts(doctor.doctorId(), day).isEmpty());

        // đúng 12 giờ và slot 120 phút là hợp lệ
        createSchedule(doctor.token(), day, "07:00", "19:00", 120, "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotMinutes").value(120))
                .andExpect(jsonPath("$.totalSlots").value(6));
    }

    @Test
    @DisplayName("AC-03.2 Một ca chia tối đa 100 slot: 101 slot → 400, đúng 100 slot → 201")
    void atMostOneHundredSlotsPerShift() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();

        createSchedule(doctor.token(), day, "08:00", "16:25", 5, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.slotMinutes").isNotEmpty());
        assertTrue(publicSlotStarts(doctor.doctorId(), day).isEmpty());

        createSchedule(doctor.token(), day, "08:00", "16:20", 5, "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalSlots").value(100));
        assertEquals(100, publicSlotStarts(doctor.doctorId(), day).size());
    }

    @Test
    @DisplayName("AC-03.2 Mỗi bác sĩ tối đa 4 ca một ngày: ca thứ 5 → 400 ở workDate; ngày khác vẫn tạo được")
    void atMostFourShiftsPerDay() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();
        for (int hour : new int[] { 7, 9, 11, 13 }) {
            createOk(doctor.token(), day, "%02d:00".formatted(hour), "%02d:00".formatted(hour + 1), 30, "[]");
        }

        createSchedule(doctor.token(), day, "15:00", "16:00", 30, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.workDate").isNotEmpty());
        assertEquals(4, schedules.findByDoctorIdAndWorkDate(doctor.doctorId(), day).size());
        assertEquals(8, publicSlotStarts(doctor.doctorId(), day).size());

        createSchedule(doctor.token(), freshDay(), "15:00", "16:00", 30, "[]").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-03.2 Mỗi ca tối đa 10 giờ nghỉ: tạo ca kèm 11 giờ nghỉ → 400; thêm giờ nghỉ thứ 11 → 400")
    void atMostTenBreaksPerShift() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();

        createSchedule(doctor.token(), day, "08:00", "12:00", 5, breaks(11))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.breaks").isNotEmpty());
        assertTrue(schedules.findByDoctorIdAndWorkDate(doctor.doctorId(), day).isEmpty());

        long id = createOk(doctor.token(), day, "08:00", "12:00", 5, breaks(10));
        assertEquals(10, getSchedule(doctor.token(), id).get("breaks").size());
        assertEquals(38, getSchedule(doctor.token(), id).get("totalSlots").asInt()); // 48 ô trừ 10 ô nghỉ

        mvc.perform(post(URL + "/" + id + "/breaks").header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"startTime\":\"11:00\",\"endTime\":\"11:30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.breaks").isNotEmpty());
        assertEquals(10, getSchedule(doctor.token(), id).get("breaks").size());
        assertEquals(38, getSchedule(doctor.token(), id).get("totalSlots").asInt());
    }

    @Test
    @DisplayName("AC-03.2 Sửa ca cũng chịu các giới hạn trên; yêu cầu sai không làm thay đổi ca")
    void updateObeysTheSameLimits() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();
        long id = createOk(doctor.token(), day, "08:00", "10:00", 30, "[]");

        String[][] invalid = {
                { "{\"slotMinutes\":7}", "slotMinutes" },
                { "{\"slotMinutes\":150}", "slotMinutes" },
                { "{\"startTime\":\"06:00\",\"endTime\":\"19:00\"}", "endTime" },
                { "{\"startTime\":\"08:00\",\"endTime\":\"17:00\",\"slotMinutes\":5}", "slotMinutes" },
                { "{\"endTime\":\"07:00\"}", "endTime" } };
        for (String[] attempt : invalid) {
            mvc.perform(put(URL + "/" + id).header("Authorization", doctor.token())
                    .contentType(MediaType.APPLICATION_JSON).content(attempt[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details." + attempt[1]).isNotEmpty());
        }
        assertEquals("10:00:00", getSchedule(doctor.token(), id).get("endTime").asText());
        assertEquals(30, getSchedule(doctor.token(), id).get("slotMinutes").asInt());
        assertEquals(4, publicSlotStarts(doctor.doctorId(), day).size());
    }

    @Test
    @DisplayName("AC-03.2 Danh sách ca: mặc định 62 ngày kể từ hôm nay (hoặc từ from); khoảng dài hơn 62 ngày → 400")
    void listRangeIsLimitedToSixtyTwoDays() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate far = freshDay();
        long id = createOk(doctor.token(), far, "08:00", "09:00", 30, "[]");

        mvc.perform(get(URL).header("Authorization", doctor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0)); // ca ở rất xa nên ngoài 62 ngày mặc định
        mvc.perform(get(URL).param("from", far.minusDays(61).toString()).header("Authorization", doctor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get(URL).param("from", far.minusDays(62).toString()).header("Authorization", doctor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get(URL).param("from", far.toString()).param("to", far.plusDays(61).toString())
                .header("Authorization", doctor.token())).andExpect(status().isOk());
        mvc.perform(get(URL).param("from", far.toString()).param("to", far.plusDays(62).toString())
                .header("Authorization", doctor.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.to").isNotEmpty());
        mvc.perform(get(URL).param("from", far.toString()).param("to", far.minusDays(1).toString())
                .header("Authorization", doctor.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.to").isNotEmpty());
    }
}
