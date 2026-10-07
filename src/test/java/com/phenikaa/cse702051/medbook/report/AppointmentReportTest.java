package com.phenikaa.cse702051.medbook.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.dto.report.AppointmentReportDTO;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Specialty;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.SpecialtyRepository;
import com.phenikaa.cse702051.medbook.security.AuthenticatedUser;
import com.phenikaa.cse702051.medbook.service.AppointmentReportService;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

import jakarta.persistence.EntityManagerFactory;

/**
 * AC-06.2…06.4, AC-06.8: báo cáo lịch khám đúng số liệu với dữ liệu có số lượng đã biết, các bất biến luôn giữ,
 * khoảng ngày được kiểm, và tổng hợp chạy ở CSDL (đếm số truy vấn bằng thống kê Hibernate).
 */
class AppointmentReportTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "/api/v1/admin/reports/appointments";

    @Autowired
    private ReportFixtures fixtures;
    @Autowired
    private DoctorRepository doctors;
    @Autowired
    private SpecialtyRepository specialties;
    @Autowired
    private AppointmentReportService reportService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private JsonNode report(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static JsonNode group(JsonNode report, String key) {
        for (JsonNode node : report.get("groups")) {
            if (key.equals(node.get("key").asText())) {
                return node;
            }
        }
        throw new AssertionError("Không có nhóm " + key + " trong " + report.get("groups"));
    }

    private static void assertInvariants(JsonNode report) {
        long total = report.get("total").asLong();
        assertEquals(total, report.get("booked").asLong() + report.get("inProgress").asLong()
                + report.get("completed").asLong() + report.get("cancelled").asLong(),
                "total phải bằng tổng bốn trạng thái");
        if (!"NONE".equals(report.get("groupBy").asText())) {
            long groupTotal = 0;
            long groupCancelled = 0;
            long groupCompleted = 0;
            for (JsonNode node : report.get("groups")) {
                groupTotal += node.get("total").asLong();
                groupCancelled += node.get("cancelled").asLong();
                groupCompleted += node.get("completed").asLong();
            }
            assertEquals(total, groupTotal, "tổng các nhóm phải bằng tổng chung");
            assertEquals(report.get("cancelled").asLong(), groupCancelled);
            assertEquals(report.get("completed").asLong(), groupCompleted);
        }
    }

    @Test
    @DisplayName("AC-06.2 Không nhóm: đếm đúng từng trạng thái, total bằng tổng bốn trạng thái, tỷ lệ hủy làm tròn 2 chữ số")
    void totalsByStatus() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rpt_total_doc");
        IsolatedPatient patient = data.isolatedPatient("rpt_total_pat");
        LocalDate day = ReportFixtures.freshDay();
        fixtures.appointments(doctor.doctorId(), patient.patientId(), day, AppointmentStatus.BOOKED, 3);
        fixtures.appointments(doctor.doctorId(), patient.patientId(), day, AppointmentStatus.IN_PROGRESS, 1);
        fixtures.appointments(doctor.doctorId(), patient.patientId(), day.plusDays(1), AppointmentStatus.COMPLETED, 4);
        fixtures.appointments(doctor.doctorId(), patient.patientId(), day.plusDays(1), AppointmentStatus.CANCELLED, 1);

        JsonNode report = report(get(URL).param("from", day.toString()).param("to", day.plusDays(1).toString())
                .param("doctorId", String.valueOf(doctor.doctorId())));

        assertEquals(day.toString(), report.get("from").asText());
        assertEquals(day.plusDays(1).toString(), report.get("to").asText());
        assertEquals("NONE", report.get("groupBy").asText());
        assertEquals(9, report.get("total").asInt());
        assertEquals(3, report.get("booked").asInt());
        assertEquals(1, report.get("inProgress").asInt());
        assertEquals(4, report.get("completed").asInt());
        assertEquals(1, report.get("cancelled").asInt());
        assertEquals(0, new BigDecimal("11.11").compareTo(report.get("cancellationRate").decimalValue()),
                "1/9 = 11,11%");
        assertEquals(0, report.get("groups").size());
        assertInvariants(report);
    }

    @Test
    @DisplayName("AC-06.2 Không có lịch nào trong khoảng: mọi số bằng 0, tỷ lệ hủy bằng 0, không lỗi chia cho 0")
    void emptyRangeGivesZeros() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rpt_empty_doc");
        LocalDate day = ReportFixtures.freshDay();

        for (String groupBy : new String[] { "NONE", "DAY", "DOCTOR", "SPECIALTY" }) {
            JsonNode report = report(get(URL).param("from", day.toString()).param("to", day.toString())
                    .param("doctorId", String.valueOf(doctor.doctorId())).param("groupBy", groupBy));
            assertEquals(0, report.get("total").asInt());
            assertEquals(0, report.get("cancelled").asInt());
            assertEquals(0, BigDecimal.ZERO.compareTo(report.get("cancellationRate").decimalValue()));
            assertEquals(0, report.get("groups").size());
        }
    }

    @Test
    @DisplayName("AC-06.3 Nhóm theo ngày: mỗi ngày một nhóm theo thứ tự tăng dần, nhãn dd/MM/yyyy, số liệu đúng")
    void groupsByDay() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rpt_day_doc");
        IsolatedPatient patient = data.isolatedPatient("rpt_day_pat");
        LocalDate day = ReportFixtures.freshDay();
        fixtures.appointments(doctor.doctorId(), patient.patientId(), day, AppointmentStatus.COMPLETED, 2);
        fixtures.appointments(doctor.doctorId(), patient.patientId(), day, AppointmentStatus.CANCELLED, 2);
        fixtures.appointments(doctor.doctorId(), patient.patientId(), day.plusDays(2), AppointmentStatus.BOOKED, 3);

        // groupBy không phân biệt hoa thường
        JsonNode report = report(get(URL).param("from", day.toString()).param("to", day.plusDays(2).toString())
                .param("doctorId", String.valueOf(doctor.doctorId())).param("groupBy", "day"));

        assertEquals("DAY", report.get("groupBy").asText());
        assertEquals(2, report.get("groups").size(), "ngày không có lịch không tạo nhóm");
        assertEquals(day.toString(), report.get("groups").get(0).get("key").asText());
        assertEquals(day.plusDays(2).toString(), report.get("groups").get(1).get("key").asText());
        JsonNode first = group(report, day.toString());
        assertEquals("%02d/%02d/%d".formatted(day.getDayOfMonth(), day.getMonthValue(), day.getYear()),
                first.get("label").asText());
        assertEquals(4, first.get("total").asInt());
        assertEquals(2, first.get("completed").asInt());
        assertEquals(2, first.get("cancelled").asInt());
        assertEquals(0, new BigDecimal("50.00").compareTo(first.get("cancellationRate").decimalValue()));
        JsonNode second = group(report, day.plusDays(2).toString());
        assertEquals(3, second.get("total").asInt());
        assertEquals(0, second.get("cancelled").asInt());
        assertEquals(7, report.get("total").asInt());
        assertEquals(0, new BigDecimal("28.57").compareTo(report.get("cancellationRate").decimalValue()), "2/7");
        assertInvariants(report);
    }

    @Test
    @DisplayName("AC-06.3 Nhóm theo bác sĩ và theo chuyên khoa: nhãn là tên, số liệu đúng, tổng các nhóm bằng tổng chung")
    void groupsByDoctorAndSpecialty() throws Exception {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Specialty specialty = specialties.save(Specialty.builder().code("RGS" + token).name("Khoa nhóm " + token)
                .status("ACTIVE").build());
        IsolatedDoctor withSpecialty = data.isolatedDoctor("rpt_grp_a_" + token);
        IsolatedDoctor withoutSpecialty = data.isolatedDoctor("rpt_grp_b_" + token);
        Doctor profile = doctors.findById(withSpecialty.doctorId()).orElseThrow();
        profile.setSpecialty(specialty);
        doctors.save(profile);
        IsolatedPatient patient = data.isolatedPatient("rpt_grp_pat");
        LocalDate day = ReportFixtures.freshDay();
        fixtures.appointments(withSpecialty.doctorId(), patient.patientId(), day, AppointmentStatus.COMPLETED, 3);
        fixtures.appointments(withSpecialty.doctorId(), patient.patientId(), day, AppointmentStatus.CANCELLED, 1);
        fixtures.appointments(withoutSpecialty.doctorId(), patient.patientId(), day, AppointmentStatus.BOOKED, 2);

        JsonNode byDoctor = report(get(URL).param("from", day.toString()).param("to", day.toString())
                .param("groupBy", "DOCTOR"));
        assertEquals(2, byDoctor.get("groups").size());
        JsonNode a = group(byDoctor, String.valueOf(withSpecialty.doctorId()));
        assertEquals("Bác sĩ thử nghiệm rpt_grp_a_" + token, a.get("label").asText());
        assertEquals(4, a.get("total").asInt());
        assertEquals(3, a.get("completed").asInt());
        assertEquals(1, a.get("cancelled").asInt());
        assertEquals(0, new BigDecimal("25.00").compareTo(a.get("cancellationRate").decimalValue()));
        assertEquals(2, group(byDoctor, String.valueOf(withoutSpecialty.doctorId())).get("total").asInt());
        assertEquals(6, byDoctor.get("total").asInt());
        assertInvariants(byDoctor);

        JsonNode bySpecialty = report(get(URL).param("from", day.toString()).param("to", day.toString())
                .param("groupBy", "SPECIALTY"));
        assertEquals(2, bySpecialty.get("groups").size());
        JsonNode named = group(bySpecialty, String.valueOf(specialty.getId()));
        assertEquals("Khoa nhóm " + token, named.get("label").asText());
        assertEquals(4, named.get("total").asInt());
        JsonNode unassigned = group(bySpecialty, "none");
        assertEquals("Chưa phân chuyên khoa", unassigned.get("label").asText());
        assertEquals(2, unassigned.get("total").asInt());
        assertInvariants(bySpecialty);

        // lọc theo chuyên khoa: chỉ còn lịch của bác sĩ thuộc chuyên khoa đó
        JsonNode filtered = report(get(URL).param("from", day.toString()).param("to", day.toString())
                .param("specialtyId", String.valueOf(specialty.getId())).param("groupBy", "DOCTOR"));
        assertEquals(4, filtered.get("total").asInt());
        assertEquals(1, filtered.get("groups").size());
    }

    @Test
    @DisplayName("AC-06.4 Khoảng ngày: quá 366 ngày, from sau to, groupBy lạ → 400; không truyền ngày → 30 ngày gần nhất")
    void validatesRangeAndDefaultsToLast30Days() throws Exception {
        LocalDate today = LocalDate.now();
        mvc.perform(get(URL).param("from", "2026-01-01").param("to", "2027-01-02")
                .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.to").value("Khoảng thời gian tối đa 366 ngày"));
        mvc.perform(get(URL).param("from", "2026-01-01").param("to", "2027-01-01")
                .header("Authorization", adminToken())).andExpect(status().isOk()); // đúng 366 ngày
        mvc.perform(get(URL).param("from", "2026-05-02").param("to", "2026-05-01")
                .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.to").exists());
        mvc.perform(get(URL).param("groupBy", "WEEK").header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.groupBy").exists());
        mvc.perform(get(URL).param("from", "khong-phai-ngay").header("Authorization", adminToken()))
                .andExpect(status().isBadRequest());

        // lịch khám 5 ngày trước nằm trong 30 ngày gần nhất; lịch 40 ngày trước thì không
        IsolatedDoctor doctor = data.isolatedDoctor("rpt_default_doc");
        IsolatedPatient patient = data.isolatedPatient("rpt_default_pat");
        fixtures.appointments(doctor.doctorId(), patient.patientId(), today.minusDays(5), AppointmentStatus.COMPLETED, 2);
        fixtures.appointment(doctor.doctorId(), patient.patientId(), today.minusDays(40), AppointmentStatus.COMPLETED);
        JsonNode report = report(get(URL).param("doctorId", String.valueOf(doctor.doctorId())));
        assertEquals(today.minusDays(29).toString(), report.get("from").asText());
        assertEquals(today.toString(), report.get("to").asText());
        assertEquals(2, report.get("total").asInt());
    }

    @Test
    @DisplayName("AC-06.8 Báo cáo trên hơn 1.000 lịch hẹn dùng không quá 5 câu truy vấn, số liệu vẫn đúng")
    void reportOverThousandAppointmentsUsesFewQueries() {
        IsolatedDoctor doctorA = data.isolatedDoctor("rpt_perf_a");
        IsolatedDoctor doctorB = data.isolatedDoctor("rpt_perf_b");
        IsolatedPatient patient = data.isolatedPatient("rpt_perf_pat");
        LocalDate day = ReportFixtures.freshDay();
        fixtures.appointments(doctorA.doctorId(), patient.patientId(), day, AppointmentStatus.COMPLETED, 600);
        fixtures.appointments(doctorA.doctorId(), patient.patientId(), day.plusDays(1), AppointmentStatus.CANCELLED, 150);
        fixtures.appointments(doctorB.doctorId(), patient.patientId(), day.plusDays(2), AppointmentStatus.BOOKED, 300);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(ADMIN_USER_ID, "admin1", Set.of("ADMIN")), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        for (String groupBy : new String[] { "NONE", "DAY", "DOCTOR", "SPECIALTY" }) {
            statistics.clear();
            AppointmentReportDTO report = reportService.report(day, day.plusDays(2), null, null, groupBy);
            long queries = statistics.getPrepareStatementCount();

            assertEquals(1050, report.total(), "groupBy=" + groupBy);
            assertEquals(600, report.completed());
            assertEquals(150, report.cancelled());
            assertEquals(300, report.booked());
            assertEquals(0, new BigDecimal("14.29").compareTo(report.cancellationRate()), "150/1050");
            assertTrue(queries >= 1 && queries <= 5, "groupBy=" + groupBy + " dùng " + queries + " câu truy vấn");
        }
        statistics.setStatisticsEnabled(false);
    }
}
