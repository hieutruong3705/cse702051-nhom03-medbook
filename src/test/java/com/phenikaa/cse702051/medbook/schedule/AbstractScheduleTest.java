package com.phenikaa.cse702051.medbook.schedule;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorScheduleRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;

/**
 * Nền chung cho test lịch làm việc: mỗi test dựng bác sĩ riêng và dùng ngày tương lai riêng nên không ảnh hưởng
 * nhau dù DB dùng chung giữa các test.
 */
abstract class AbstractScheduleTest extends AbstractApiTest {

    protected static final ObjectMapper JSON = new ObjectMapper();
    protected static final String URL = "/api/v1/doctor-schedules";
    protected static final long SERVICE_ID = 1L; // seed: DV01 Khám tổng quát, ACTIVE

    private static final AtomicInteger DAY = new AtomicInteger();

    @Autowired
    protected AppointmentSlotRepository slots;
    @Autowired
    protected DoctorScheduleRepository schedules;

    protected static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    protected IsolatedDoctor newDoctor() {
        return data.isolatedDoctor("sch" + unique());
    }

    protected static LocalDate freshDay() {
        return LocalDate.now().plusDays(1100L + DAY.incrementAndGet());
    }

    protected ResultActions createSchedule(String token, LocalDate date, String start, String end, Object minutes,
            String breaksJson) throws Exception {
        String body = "{\"workDate\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"slotMinutes\":%s,\"breaks\":%s}"
                .formatted(date, start, end, minutes, breaksJson);
        return mvc.perform(post(URL).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected long createOk(String token, LocalDate date, String start, String end, int minutes, String breaksJson)
            throws Exception {
        return json(createSchedule(token, date, start, end, minutes, breaksJson).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    protected JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected JsonNode getSchedule(String token, long id) throws Exception {
        return json(mvc.perform(get(URL + "/" + id).header("Authorization", token)).andExpect(status().isOk()));
    }

    /** Giờ bắt đầu của các slot còn đặt được, đúng như bệnh nhân nhìn thấy. */
    protected List<String> publicSlotStarts(long doctorId, LocalDate date) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/doctors/%d/slots".formatted(doctorId))
                .param("date", date.toString())).andExpect(status().isOk()).andReturn();
        List<String> starts = new ArrayList<>();
        JSON.readTree(result.getResponse().getContentAsString())
                .forEach(node -> starts.add(node.get("startTime").asText()));
        return starts;
    }

    /** Đặt slot trống sớm nhất của bác sĩ trong ngày bằng tài khoản bệnh nhân cho trước; trả mã lịch hẹn. */
    protected long bookFirstSlot(String patientToken, long doctorId, LocalDate date) throws Exception {
        long slotId = firstFreeSlotId(doctorId, date);
        return json(book(patientToken, slotId).andExpect(status().isCreated())).get("id").asLong();
    }

    protected long firstFreeSlotId(long doctorId, LocalDate date) {
        return slots.findByDoctorIdAndSlotDateAndIsAvailableTrue(doctorId, date).stream()
                .min((a, b) -> a.getStartTime().compareTo(b.getStartTime())).orElseThrow().getId();
    }

    protected ResultActions book(String patientToken, long slotId) throws Exception {
        return mvc.perform(post("/api/v1/appointments").header("Authorization", patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":%d}".formatted(slotId, SERVICE_ID)));
    }
}
