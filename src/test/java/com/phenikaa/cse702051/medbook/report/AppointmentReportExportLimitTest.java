package com.phenikaa.cse702051.medbook.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-06.6: vượt giới hạn số dòng mỗi lần xuất → 422 và không ghi audit xuất tệp. Giới hạn thật là 50.000 dòng;
 * ở đây hạ xuống 3 bằng cấu hình để kiểm đúng nhánh xử lý mà không phải dựng 50.001 lịch hẹn (vì đổi cấu hình
 * nên test chạy trong Spring context riêng).
 */
@TestPropertySource(properties = "medbook.reports.export-max-rows=3")
class AppointmentReportExportLimitTest extends AbstractApiTest {

    private static final String URL = "/api/v1/admin/reports/appointments/export";

    @Autowired
    private ReportFixtures fixtures;

    @Test
    @DisplayName("Số dòng vượt giới hạn → 422 kèm hướng dẫn thu hẹp khoảng ngày; đúng bằng giới hạn thì vẫn xuất được")
    void rejectsExportOverRowLimit() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("exp_limit_doc");
        IsolatedPatient patient = data.isolatedPatient("exp_limit_pat");
        LocalDate day = ReportFixtures.freshDay();
        fixtures.appointments(doctor.doctorId(), patient.patientId(), day, AppointmentStatus.BOOKED, 3);
        fixtures.appointment(doctor.doctorId(), patient.patientId(), day.plusDays(1), AppointmentStatus.BOOKED);
        String doctorId = String.valueOf(doctor.doctorId());
        long auditsBefore = auditLogs.findByActionCode(AuditActions.REPORT_EXPORT).size();

        mvc.perform(get(URL).param("from", day.toString()).param("to", day.plusDays(1).toString())
                .param("doctorId", doctorId).header("Authorization", adminToken()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.message").value(
                        "Báo cáo có 4 dòng, vượt giới hạn 3 dòng mỗi lần xuất. Vui lòng thu hẹp khoảng ngày!"));
        assertEquals(auditsBefore, auditLogs.findByActionCode(AuditActions.REPORT_EXPORT).size(),
                "yêu cầu bị từ chối không được ghi audit xuất tệp");

        mvc.perform(get(URL).param("from", day.toString()).param("to", day.toString())
                .param("doctorId", doctorId).header("Authorization", adminToken()))
                .andExpect(status().isOk());
        assertEquals(auditsBefore + 1, auditLogs.findByActionCode(AuditActions.REPORT_EXPORT).size());
    }
}
