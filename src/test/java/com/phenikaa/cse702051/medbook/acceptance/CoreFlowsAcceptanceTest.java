package com.phenikaa.cse702051.medbook.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * Nghiệm thu hai luồng cốt lõi theo tài liệu (FE-02 kịch bản 1, FE-03 kịch bản 2) bằng API thật, từng bước nối
 * tiếp nhau trên cùng dữ liệu:
 *
 * <ol>
 * <li><b>Đặt lịch khám:</b> bác sĩ tạo ca làm việc → hệ thống sinh giờ trống → bệnh nhân đăng nhập, chọn giờ,
 * đặt lịch → xem lại lịch đã đặt (người khác không đặt trùng, không xem được).</li>
 * <li><b>Khám và xem bệnh án:</b> bác sĩ bắt đầu khám từ lịch hẹn → ghi lần khám/bệnh án, kê đơn → hoàn thành →
 * bệnh nhân xem đúng bệnh án của mình; bệnh nhân khác, bác sĩ khác và Admin bị chặn.</li>
 * </ol>
 */
class CoreFlowsAcceptanceTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long SERVICE_ID = 1L; // seed: DV01 Khám tổng quát, ACTIVE

    private ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", token));
    }

    private JsonNode jsonOf(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token, String body) throws Exception {
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions book(String token, long slotId) throws Exception {
        return send(post("/api/v1/appointments"), token,
                "{\"slotId\":%d,\"serviceId\":%d,\"notes\":\"Ho và sốt nhẹ\"}".formatted(slotId, SERVICE_ID));
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    @Test
    @DisplayName("Luồng 1 + 2: tạo ca → giờ trống → đặt lịch → xem lịch → khám → bệnh án → bệnh nhân xem; người khác bị chặn")
    void scheduleBookExamineAndViewRecord() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("flow_doc");
        IsolatedDoctor otherDoctor = data.isolatedDoctor("flow_other_doc");
        IsolatedPatient patient = data.isolatedPatient("flow_pat");
        IsolatedPatient stranger = data.isolatedPatient("flow_stranger");
        LocalDate day = LocalDate.now().plusDays(700);

        // ===== Luồng 1: Đặt lịch khám =====

        // 1. Bác sĩ có lịch làm việc
        send(post("/api/v1/doctor-schedules"), doctor.token(),
                "{\"workDate\":\"%s\",\"startTime\":\"08:00\",\"endTime\":\"09:30\",\"slotMinutes\":30,\"breaks\":[]}"
                        .formatted(day))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalSlots").value(3));

        // 2. Hệ thống có giờ trống (bệnh nhân chưa cần đăng nhập để xem)
        JsonNode freeSlots = jsonOf(mvc.perform(get("/api/v1/doctors/%d/slots".formatted(doctor.doctorId()))
                .param("date", day.toString())).andExpect(status().isOk()));
        assertEquals(3, freeSlots.size());
        long slotId = freeSlots.get(0).get("id").asLong();

        // 3. Bệnh nhân đăng nhập, chọn giờ và đặt lịch; người khác đặt cùng giờ → 409
        MvcResult booked = book(patient.token(), slotId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.patientId").value(patient.patientId()))
                .andExpect(jsonPath("$.doctorId").value(doctor.doctorId()))
                .andReturn();
        long appointmentId = JSON.readTree(booked.getResponse().getContentAsString()).get("id").asLong();
        book(stranger.token(), slotId).andExpect(status().isConflict());
        assertEquals(2, jsonOf(mvc.perform(get("/api/v1/doctors/%d/slots".formatted(doctor.doctorId()))
                .param("date", day.toString()))).size(), "slot đã đặt không còn trong danh sách giờ trống");

        // 4. Xem lại lịch đã đặt: đúng của mình; người khác không thấy và không mở được
        JsonNode mine = jsonOf(getAs(patient.token(), "/api/v1/appointments/me").andExpect(status().isOk()))
                .get("content");
        assertTrue(ids(mine).contains(appointmentId));
        assertFalse(ids(jsonOf(getAs(stranger.token(), "/api/v1/appointments/me"))
                .get("content")).contains(appointmentId));
        getAs(stranger.token(), "/api/v1/appointments/" + appointmentId).andExpect(status().isForbidden());
        getAs(patient.token(), "/api/v1/appointments/" + appointmentId)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BOOKED"));

        // 5. Bác sĩ thấy lịch trong danh sách của mình (kèm tên bệnh nhân); bác sĩ khác thì không
        JsonNode doctorList = jsonOf(getAs(doctor.token(), "/api/v1/appointments/me").andExpect(status().isOk()))
                .get("content");
        assertTrue(ids(doctorList).contains(appointmentId));
        assertEquals(patient.patientId(), doctorList.get(0).get("patientId").asLong());
        assertFalse(ids(jsonOf(getAs(otherDoctor.token(), "/api/v1/appointments/me"))
                .get("content")).contains(appointmentId));

        // ===== Luồng 2: Khám và xem bệnh án =====

        // 6. Chỉ bác sĩ phụ trách mới bắt đầu khám được; bệnh nhân/bác sĩ khác bị chặn
        String startBody = "{\"appointmentId\":%d,\"chiefComplaint\":\"Ho và sốt nhẹ\"}".formatted(appointmentId);
        send(post("/api/v1/encounters"), otherDoctor.token(), startBody).andExpect(status().isForbidden());
        send(post("/api/v1/encounters"), patient.token(), startBody).andExpect(status().isForbidden());

        MvcResult started = send(post("/api/v1/encounters"), doctor.token(), startBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.patientId").value(patient.patientId()))
                .andExpect(jsonPath("$.doctorId").value(doctor.doctorId()))
                .andReturn();
        long encounterId = JSON.readTree(started.getResponse().getContentAsString()).get("id").asLong();
        getAs(patient.token(), "/api/v1/appointments/" + appointmentId)
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // 7. Bác sĩ ghi lần khám và tóm tắt bệnh án
        send(put("/api/v1/encounters/" + encounterId), doctor.token(), """
                {"diagnosis":"Viêm họng cấp","clinicalNotes":"Họng đỏ, không sốt cao",
                 "treatmentPlan":"Nghỉ ngơi, uống nhiều nước","followUpNote":"Tái khám sau 5 ngày",
                 "clinicalSummary":{"bloodType":"A+","allergyNotes":"Không"}}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Viêm họng cấp"))
                .andExpect(jsonPath("$.editable").value(true));

        // 8. Bác sĩ kê đơn thuốc cho lần khám
        MvcResult prescription = send(post("/api/v1/encounters/%d/prescriptions".formatted(encounterId)),
                doctor.token(), "{\"notes\":\"Uống sau ăn\"}").andExpect(status().isOk()).andReturn();
        long prescriptionId = JSON.readTree(prescription.getResponse().getContentAsString()).get("id").asLong();
        send(post("/api/v1/prescriptions/%d/items".formatted(prescriptionId)), doctor.token(), """
                {"medicineName":"Paracetamol 500mg","dosage":"1 viên","frequency":"2 lần/ngày",
                 "durationDays":5,"quantity":10,"instructions":"Uống sau ăn"}
                """).andExpect(status().isOk());

        // 9. Hoàn thành khám: lịch → COMPLETED, sau đó nội dung bị khóa
        send(put("/api/v1/encounters/" + encounterId), doctor.token(), "{\"status\":\"COMPLETED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.editable").value(false));
        getAs(patient.token(), "/api/v1/appointments/" + appointmentId)
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        send(put("/api/v1/encounters/" + encounterId), doctor.token(), "{\"diagnosis\":\"Sửa lại\"}")
                .andExpect(status().isConflict());

        // 10. Bệnh nhân xem đúng bệnh án và lần khám của mình
        JsonNode history = jsonOf(getAs(patient.token(), "/api/v1/encounters/me").andExpect(status().isOk()))
                .get("content");
        assertEquals(List.of(encounterId), ids(history));
        assertEquals("Viêm họng cấp", history.get(0).get("diagnosis").asText());

        getAs(patient.token(), "/api/v1/encounters/" + encounterId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Viêm họng cấp"))
                .andExpect(jsonPath("$.treatmentPlan").value("Nghỉ ngơi, uống nhiều nước"))
                .andExpect(jsonPath("$.editable").value(false));
        JsonNode record = jsonOf(getAs(patient.token(), "/api/v1/medical-records/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloodType").value("A+"))
                .andExpect(jsonPath("$.patientId").value(patient.patientId())));
        long recordId = record.get("id").asLong();
        getAs(patient.token(), "/api/v1/medical-records/" + recordId).andExpect(status().isOk());
        getAs(patient.token(), "/api/v1/encounters/%d/prescriptions".formatted(encounterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(prescriptionId));
        getAs(patient.token(), "/api/v1/prescriptions/%d/items".formatted(prescriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].medicineName").value("Paracetamol 500mg"));

        // 11. Người khác bị chặn: bệnh nhân khác, bác sĩ không phụ trách, Admin
        for (String intruder : List.of(stranger.token(), otherDoctor.token(), adminToken())) {
            getAs(intruder, "/api/v1/encounters/" + encounterId).andExpect(status().isForbidden());
            getAs(intruder, "/api/v1/medical-records/" + recordId).andExpect(status().isForbidden());
            getAs(intruder, "/api/v1/encounters/%d/prescriptions".formatted(encounterId))
                    .andExpect(status().isForbidden());
        }
        assertTrue(ids(jsonOf(getAs(stranger.token(), "/api/v1/encounters/me")).get("content")).isEmpty(),
                "lịch sử khám của người khác không lẫn vào");
        // bệnh nhân khác không kê/sửa được đơn thuốc của lần khám này
        send(post("/api/v1/encounters/%d/prescriptions".formatted(encounterId)), stranger.token(), "{}")
                .andExpect(status().isForbidden());
    }
}
