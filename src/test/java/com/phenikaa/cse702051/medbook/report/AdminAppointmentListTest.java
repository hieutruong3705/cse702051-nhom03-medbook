package com.phenikaa.cse702051.medbook.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Specialty;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.SpecialtyRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-06.1, AC-06.7: Admin xem danh sách lịch hẹn ở mức hành chính, lọc theo ngày khám, bác sĩ, chuyên khoa,
 * trạng thái; JSON không bao giờ có ghi chú hay lý do hủy.
 */
class AdminAppointmentListTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "/api/v1/admin/appointments";

    @Autowired
    private ReportFixtures fixtures;
    @Autowired
    private DoctorRepository doctors;
    @Autowired
    private SpecialtyRepository specialties;

    private JsonNode list(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    @Test
    @DisplayName("Danh sách: đủ cột hành chính, mới nhất trước, có phân trang; không có ghi chú hay lý do hủy")
    void listsAdministrativeColumnsOnly() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("adm_list_doc");
        IsolatedPatient patient = data.isolatedPatient("adm_list_pat");
        LocalDate day = ReportFixtures.freshDay();
        Appointment early = fixtures.appointment(doctor.doctorId(), patient.patientId(), day,
                AppointmentStatus.BOOKED, 1L, "GHICHU-LAMSANG-4410 ho ra máu", null);
        Appointment late = fixtures.appointment(doctor.doctorId(), patient.patientId(), day.plusDays(1),
                AppointmentStatus.CANCELLED, null, "ghi chú khác", "LYDO-HUY-4411 đã khỏi bệnh");

        MvcResult result = mvc.perform(get(URL).param("doctorId", String.valueOf(doctor.doctorId()))
                .param("from", day.toString()).param("to", day.plusDays(1).toString())
                .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].id").value(late.getId()))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.content[0].date").value(day.plusDays(1).toString()))
                .andExpect(jsonPath("$.content[1].id").value(early.getId()))
                .andExpect(jsonPath("$.content[1].patientName").value("Bệnh nhân thử nghiệm adm_list_pat"))
                .andExpect(jsonPath("$.content[1].patientCode").value("ISOP-adm_list_pat"))
                .andExpect(jsonPath("$.content[1].doctorName").value("Bác sĩ thử nghiệm adm_list_doc"))
                .andExpect(jsonPath("$.content[1].serviceName").value("Khám tổng quát"))
                .andExpect(jsonPath("$.content[1].startTime").isNotEmpty())
                .andExpect(jsonPath("$.content[1].endTime").isNotEmpty())
                .andExpect(jsonPath("$.content[1].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[1].notes").doesNotExist())
                .andExpect(jsonPath("$.content[1].cancelReason").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(body.contains("GHICHU-LAMSANG-4410"), "ghi chú của bệnh nhân không được lộ cho Admin");
        assertFalse(body.contains("ho ra máu"));
        assertFalse(body.contains("LYDO-HUY-4411"), "lý do hủy không được lộ cho Admin");
        assertFalse(body.contains("notes") || body.contains("cancelReason"));
    }

    @Test
    @DisplayName("Lọc theo từng tham số: khoảng ngày khám, bác sĩ, trạng thái; from sau to → 400")
    void filtersByDateDoctorAndStatus() throws Exception {
        IsolatedDoctor doctorA = data.isolatedDoctor("adm_filter_a");
        IsolatedDoctor doctorB = data.isolatedDoctor("adm_filter_b");
        IsolatedPatient patient = data.isolatedPatient("adm_filter_pat");
        LocalDate day = ReportFixtures.freshDay();
        long aBooked = fixtures.appointment(doctorA.doctorId(), patient.patientId(), day, AppointmentStatus.BOOKED)
                .getId();
        long aCompleted = fixtures.appointment(doctorA.doctorId(), patient.patientId(), day.plusDays(2),
                AppointmentStatus.COMPLETED).getId();
        long bBooked = fixtures.appointment(doctorB.doctorId(), patient.patientId(), day, AppointmentStatus.BOOKED)
                .getId();
        String from = day.toString();
        String to = day.plusDays(2).toString();

        // cả khoảng ngày (không dính lịch của test khác vì ngày là riêng)
        assertEquals(List.of(aCompleted, bBooked, aBooked).stream().sorted().toList(),
                ids(list(get(URL).param("from", from).param("to", to))).stream().sorted().toList());
        // chỉ ngày đầu
        assertEquals(List.of(aBooked, bBooked).stream().sorted().toList(),
                ids(list(get(URL).param("from", from).param("to", from))).stream().sorted().toList());
        // theo bác sĩ
        assertEquals(List.of(aCompleted, aBooked),
                ids(list(get(URL).param("from", from).param("to", to)
                        .param("doctorId", String.valueOf(doctorA.doctorId())))));
        // theo trạng thái
        assertEquals(List.of(aCompleted),
                ids(list(get(URL).param("from", from).param("to", to).param("status", "COMPLETED"))));
        // tổ hợp bác sĩ + trạng thái; tham số rỗng (giao diện gửi khi không lọc) được bỏ qua
        assertEquals(List.of(bBooked), ids(list(get(URL).param("from", from).param("to", to)
                .param("doctorId", String.valueOf(doctorB.doctorId())).param("status", "BOOKED"))));
        assertEquals(3, list(get(URL).param("from", from).param("to", to).param("doctorId", "")
                .param("status", "")).get("totalElements").asInt());

        mvc.perform(get(URL).param("from", to).param("to", from).header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.to").exists());
        mvc.perform(get(URL).param("status", "KHONG_CO").header("Authorization", adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Lọc theo chuyên khoa, kèm tên chuyên khoa; phân trang chia đúng và size bị cắt còn 100")
    void filtersBySpecialtyAndPaginates() throws Exception {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Specialty specialty = specialties.save(Specialty.builder().code("RPT" + token).name("Khoa báo cáo " + token)
                .status("ACTIVE").build());
        IsolatedDoctor inSpecialty = data.isolatedDoctor("adm_spec_in_" + token);
        IsolatedDoctor outside = data.isolatedDoctor("adm_spec_out_" + token);
        Doctor profile = doctors.findById(inSpecialty.doctorId()).orElseThrow();
        profile.setSpecialty(specialty);
        doctors.save(profile);
        IsolatedPatient patient = data.isolatedPatient("adm_spec_pat");
        LocalDate day = ReportFixtures.freshDay();
        fixtures.appointments(inSpecialty.doctorId(), patient.patientId(), day, AppointmentStatus.BOOKED, 5);
        fixtures.appointment(outside.doctorId(), patient.patientId(), day, AppointmentStatus.BOOKED);

        JsonNode page = list(get(URL).param("from", day.toString()).param("to", day.toString())
                .param("specialtyId", String.valueOf(specialty.getId())).param("size", "2"));
        assertEquals(5, page.get("totalElements").asInt());
        assertEquals(3, page.get("totalPages").asInt());
        assertEquals(2, page.get("content").size());
        page.get("content").forEach(node ->
                assertEquals("Khoa báo cáo " + token, node.get("specialtyName").asText()));
        JsonNode last = list(get(URL).param("from", day.toString()).param("to", day.toString())
                .param("specialtyId", String.valueOf(specialty.getId())).param("size", "2").param("page", "2"));
        assertEquals(1, last.get("content").size());

        assertEquals(100, list(get(URL).param("from", day.toString()).param("to", day.toString())
                .param("size", "500")).get("size").asInt());
        // bác sĩ chưa gán chuyên khoa: vẫn hiện trong danh sách chung, cột chuyên khoa để trống
        JsonNode all = list(get(URL).param("from", day.toString()).param("to", day.toString())
                .param("doctorId", String.valueOf(outside.doctorId())));
        assertEquals(1, all.get("totalElements").asInt());
        assertTrue(all.get("content").get(0).get("specialtyName").isNull());
    }

    @Test
    @DisplayName("AC-06.7 Quyền: khách 401; bệnh nhân và bác sĩ 403 ở danh sách, báo cáo và xuất tệp")
    void onlyAdminCanUseReportEndpoints() throws Exception {
        for (String url : new String[] { URL, "/api/v1/admin/reports/appointments",
                "/api/v1/admin/reports/appointments/export" }) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).header("Authorization", patientToken())).andExpect(status().isForbidden());
            mvc.perform(get(url).header("Authorization", doctorToken())).andExpect(status().isForbidden());
        }
        // đường dẫn thống kê cũ đã bị gỡ
        mvc.perform(get("/api/v1/appointments/admin/reports").header("Authorization", adminToken()))
                .andExpect(status().is4xxClientError());
    }
}
