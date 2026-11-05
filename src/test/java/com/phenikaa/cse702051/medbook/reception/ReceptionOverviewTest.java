package com.phenikaa.cse702051.medbook.reception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * Số liệu trong ngày của quầy lễ tân. CSDL dùng chung giữa các test nên các số toàn cục được kiểm bằng chênh
 * lệch trước và sau; số theo ngày được kiểm trên một ngày riêng của test.
 */
class ReceptionOverviewTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "/api/v1/reception/overview";

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode overview(String date) throws Exception {
        var request = get(URL).header("Authorization", receptionistToken());
        if (date != null) {
            request.param("date", date);
        }
        return json(mvc.perform(request).andExpect(status().isOk()));
    }

    private long bookAtDesk(long patientId, long slotId) throws Exception {
        return json(mvc.perform(post("/api/v1/reception/appointments").header("Authorization", receptionistToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":%d,\"slotId\":%d,\"serviceId\":1}".formatted(patientId, slotId)))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    @DisplayName("Số lịch hẹn theo trạng thái đúng với ngày được hỏi và không lẫn ngày khác")
    void countsAppointmentsOfTheDayByStatus() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rov" + key);
        IsolatedPatient patient = data.isolatedPatient("rov" + key);
        AppointmentSlot first = data.futureSlot(doctor.doctorId());
        LocalDate day = first.getSlotDate();
        AppointmentSlot second = data.slot(doctor.doctorId(), day, LocalTime.of(10, 0));
        AppointmentSlot third = data.slot(doctor.doctorId(), day, LocalTime.of(11, 0));
        AppointmentSlot otherDay = data.futureSlot(doctor.doctorId());
        bookAtDesk(patient.patientId(), first.getId());
        bookAtDesk(patient.patientId(), second.getId());
        long cancelled = bookAtDesk(patient.patientId(), third.getId());
        bookAtDesk(patient.patientId(), otherDay.getId());
        mvc.perform(patch("/api/v1/reception/appointments/" + cancelled + "/cancel")
                .header("Authorization", receptionistToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"thử tổng quan\"}")).andExpect(status().isOk());

        JsonNode result = overview(day.toString());

        assertEquals(day.toString(), result.get("date").asText());
        assertEquals(2, result.get("bookedCount").asLong());
        assertEquals(1, result.get("cancelledCount").asLong());
        assertEquals(0, result.get("inProgressCount").asLong());
        assertEquals(0, result.get("completedCount").asLong());
    }

    @Test
    @DisplayName("Thu một hóa đơn: số và tiền chưa thu giảm, số và tiền đã thu hôm nay tăng đúng bằng hóa đơn đó")
    void collectingMovesMoneyFromUnpaidToCollectedToday() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rovm" + key);
        IsolatedPatient patient = data.isolatedPatient("rovm" + key);
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        JsonNode invoice = json(mvc.perform(post("/api/v1/encounters/%d/invoice".formatted(encounter.getId()))
                .header("Authorization", doctor.token()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"serviceId\":1,\"quantity\":1}]")).andExpect(status().isOk()));
        BigDecimal total = invoice.get("totalAmount").decimalValue();
        assertTrue(total.signum() > 0);

        JsonNode before = overview(null);
        mvc.perform(patch("/api/v1/reception/invoices/" + invoice.get("id").asLong() + "/collect")
                .header("Authorization", receptionistToken())).andExpect(status().isOk());
        JsonNode after = overview(null);

        assertEquals(LocalDate.now().toString(), after.get("date").asText(), "không nêu ngày thì là hôm nay");
        assertEquals(before.get("unpaidInvoiceCount").asLong() - 1, after.get("unpaidInvoiceCount").asLong());
        assertEquals(before.get("collectedInvoiceCount").asLong() + 1, after.get("collectedInvoiceCount").asLong());
        assertEquals(0, before.get("unpaidInvoiceAmount").decimalValue().subtract(total)
                .compareTo(after.get("unpaidInvoiceAmount").decimalValue()));
        assertEquals(0, before.get("collectedAmount").decimalValue().add(total)
                .compareTo(after.get("collectedAmount").decimalValue()));
    }

    @Test
    @DisplayName("Ngày không có gì: bốn số đếm bằng 0, đã thu là 0 và 0.00 (không phải null); tiền luôn có hai chữ số thập phân")
    void emptyDayReturnsZeros() throws Exception {
        String raw = mvc.perform(get(URL).param("date", "2099-12-31").header("Authorization", receptionistToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2099-12-31"))
                .andExpect(jsonPath("$.bookedCount").value(0))
                .andExpect(jsonPath("$.inProgressCount").value(0))
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.cancelledCount").value(0))
                .andExpect(jsonPath("$.collectedInvoiceCount").value(0))
                .andExpect(jsonPath("$.unpaidInvoiceCount").isNumber())
                .andReturn().getResponse().getContentAsString();

        assertTrue(raw.contains("\"collectedAmount\":0.00"), raw);
        assertTrue(raw.matches(".*\"unpaidInvoiceAmount\":\\d+\\.\\d{2}[,}].*"), raw);
    }

    @Test
    @DisplayName("Lịch đang khám và đã hoàn thành của ngày được đếm đúng cột")
    void countsInProgressAndCompleted() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rovs" + key);
        IsolatedPatient patient = data.isolatedPatient("rovs" + key);
        // newAppointment đặt mọi lịch vào cùng một ngày (một năm sau hôm nay): so chênh lệch trên ngày đó
        String day = LocalDate.now().plusYears(1).toString();
        JsonNode before = overview(day);

        data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.IN_PROGRESS);
        data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.COMPLETED);
        data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.COMPLETED);
        JsonNode after = overview(day);

        assertEquals(before.get("inProgressCount").asLong() + 1, after.get("inProgressCount").asLong());
        assertEquals(before.get("completedCount").asLong() + 2, after.get("completedCount").asLong());
        assertEquals(before.get("bookedCount").asLong(), after.get("bookedCount").asLong());
    }

    @Test
    @DisplayName("Ngày sai định dạng 400; khách 401; bệnh nhân, bác sĩ, quản trị viên 403")
    void rejectsBadDateAndOtherRoles() throws Exception {
        mvc.perform(get(URL).param("date", "31/12/2026").header("Authorization", receptionistToken()))
                .andExpect(status().isBadRequest());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (String token : List.of(patientToken(), doctorToken(), adminToken())) {
            mvc.perform(get(URL).header("Authorization", token)).andExpect(status().isForbidden());
        }
    }
}
