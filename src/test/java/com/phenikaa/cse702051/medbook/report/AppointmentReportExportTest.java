package com.phenikaa.cse702051.medbook.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;
import com.phenikaa.cse702051.medbook.util.CsvWriter;

/**
 * AC-06.5, AC-06.6: xuất CSV có BOM UTF-8, đúng tiêu đề, đúng số dòng, tên tệp đúng; ô nguy hiểm được vô hiệu
 * hóa; mỗi lần xuất ghi một bản audit không chứa dữ liệu bệnh nhân.
 */
class AppointmentReportExportTest extends AbstractApiTest {

    private static final String URL = "/api/v1/admin/reports/appointments/export";
    private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    @Autowired
    private ReportFixtures fixtures;
    @Autowired
    private PatientRepository patients;

    @Test
    @DisplayName("AC-06.5 CSV: BOM UTF-8, tiêu đề tiếng Việt, mỗi lịch một dòng, tên tệp và Content-Disposition đúng")
    void exportsCsvWithBomHeaderAndRows() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("exp_csv_doc");
        IsolatedPatient patient = data.isolatedPatient("exp_csv_pat");
        LocalDate day = ReportFixtures.freshDay();
        fixtures.appointment(doctor.doctorId(), patient.patientId(), day, AppointmentStatus.COMPLETED, 1L,
                "GHICHU-XUAT-8801", null);
        fixtures.appointment(doctor.doctorId(), patient.patientId(), day.plusDays(1), AppointmentStatus.CANCELLED,
                null, null, "LYDO-XUAT-8802");
        fixtures.appointment(doctor.doctorId(), patient.patientId(), day.plusDays(3), AppointmentStatus.BOOKED);

        MvcResult result = mvc.perform(get(URL).param("from", day.toString()).param("to", day.plusDays(1).toString())
                .param("doctorId", String.valueOf(doctor.doctorId())).param("format", "csv")
                .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("text/csv;charset=UTF-8", result.getResponse().getContentType());
        assertEquals("attachment; filename=\"medbook-lich-kham-%s_%s.csv\"".formatted(day, day.plusDays(1)),
                result.getResponse().getHeader("Content-Disposition"));
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertArrayEquals(UTF8_BOM, Arrays.copyOf(bytes, 3), "tệp phải bắt đầu bằng BOM UTF-8");

        String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        List<String> lines = text.lines().toList();
        assertEquals(3, lines.size(), "1 dòng tiêu đề + 2 lịch trong khoảng ngày (lịch ngày thứ tư nằm ngoài)");
        assertEquals("\"Mã lịch\",\"Bệnh nhân\",\"Mã bệnh nhân\",\"Bác sĩ\",\"Chuyên khoa\",\"Dịch vụ\","
                + "\"Ngày khám\",\"Giờ bắt đầu\",\"Giờ kết thúc\",\"Trạng thái\",\"Thời điểm đặt\"", lines.get(0));
        assertTrue(text.contains("\r\n"), "dòng kết thúc bằng CRLF");
        String first = lines.get(1);
        assertTrue(first.contains("\"Bệnh nhân thử nghiệm exp_csv_pat\",\"ISOP-exp_csv_pat\","
                + "\"Bác sĩ thử nghiệm exp_csv_doc\",\"\",\"Khám tổng quát\","), first);
        assertTrue(first.contains("\"%02d/%02d/%d\"".formatted(day.getDayOfMonth(), day.getMonthValue(),
                day.getYear())), first);
        assertTrue(first.contains("\"Hoàn thành\""), first);
        assertTrue(lines.get(2).contains("\"Đã hủy\""), lines.get(2));
        assertFalse(text.contains("GHICHU-XUAT-8801"), "ghi chú không được xuất ra tệp");
        assertFalse(text.contains("LYDO-XUAT-8802"), "lý do hủy không được xuất ra tệp");

        // chỉ hỗ trợ csv
        mvc.perform(get(URL).param("format", "xlsx").header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.format").exists());
        mvc.perform(get(URL).param("from", "2026-01-01").param("to", "2027-06-01")
                .header("Authorization", adminToken())).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC-06.5 Chống chèn công thức: tên bệnh nhân bắt đầu bằng = được thêm dấu ' và dấu nháy kép được nhân đôi")
    void neutralisesFormulaCells() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("exp_inj_doc");
        Patient attacker = data.extraPatient("EXPINJ01");
        attacker.setFullName("=HYPERLINK(\"http://evil.example\",\"Bấm vào đây\")");
        patients.save(attacker);
        LocalDate day = ReportFixtures.freshDay();
        fixtures.appointment(doctor.doctorId(), attacker.getId(), day, AppointmentStatus.BOOKED);

        MvcResult result = mvc.perform(get(URL).param("from", day.toString()).param("to", day.toString())
                .param("doctorId", String.valueOf(doctor.doctorId())).header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn();
        String text = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        assertTrue(text.contains("\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"Bấm vào đây\"\")\""), text);
        assertFalse(text.contains(",\"=HYPERLINK"), "ô không được bắt đầu bằng dấu =");
    }

    @Test
    @DisplayName("CsvWriter: thoát các ký tự mở đầu công thức (= + - @ tab xuống dòng); số ghi nguyên; null thành ô rỗng")
    void csvWriterEscapesDangerousCells() {
        CsvWriter csv = new CsvWriter()
                .row("=1+1", "+84901234567", "-5", "@SUM(A1)", "\tTab", "\nXuống dòng")
                .row("Bình thường", "a,b", "nói \"xin chào\"", null, 42, 1.5);
        String text = new String(csv.toBytes(), StandardCharsets.UTF_8);

        assertEquals('﻿', text.charAt(0));
        assertEquals(2, csv.rowCount());
        assertTrue(text.contains("\"'=1+1\",\"'+84901234567\",\"'-5\",\"'@SUM(A1)\",\"'\tTab\",\"'\nXuống dòng\"\r\n"),
                text);
        assertTrue(text.endsWith("\"Bình thường\",\"a,b\",\"nói \"\"xin chào\"\"\",\"\",42,1.5\r\n"), text);
    }

    @Test
    @DisplayName("AC-06.6 Mỗi lần xuất ghi đúng một bản audit REPORT_EXPORT gồm bộ lọc và số dòng, không có dữ liệu bệnh nhân")
    void everyExportIsAudited() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("exp_audit_doc");
        IsolatedPatient patient = data.isolatedPatient("exp_audit_pat");
        LocalDate day = ReportFixtures.freshDay();
        fixtures.appointments(doctor.doctorId(), patient.patientId(), day, AppointmentStatus.BOOKED, 4);
        String doctorMarker = "\"doctorId\":" + doctor.doctorId() + ",";

        mvc.perform(get(URL).param("from", day.toString()).param("to", day.toString())
                .param("doctorId", String.valueOf(doctor.doctorId())).header("Authorization", adminToken()))
                .andExpect(status().isOk());

        List<AuditLog> exports = auditLogs.findByActionCode(AuditActions.REPORT_EXPORT).stream()
                .filter(log -> log.getMetadataJson().replace("\\", "").replace(" ", "").contains(doctorMarker))
                .toList();
        assertEquals(1, exports.size());
        AuditLog audit = exports.getFirst();
        assertEquals(AuditActions.ENTITY_APPOINTMENTS, audit.getEntityType());
        assertEquals(ADMIN_USER_ID, audit.getActorUser().getId());
        String metadata = audit.getMetadataJson().replace("\\", "").replace(" ", "");
        assertTrue(metadata.contains("\"rowCount\":4"), metadata);
        assertTrue(metadata.contains("\"from\":\"" + day + "\""), metadata);
        assertTrue(metadata.contains("\"to\":\"" + day + "\""), metadata);
        assertFalse(metadata.contains("exp_audit_pat"), "audit không được chứa tên hay mã bệnh nhân");
        assertFalse(metadata.contains("ISOP-"), metadata);

        // yêu cầu bị từ chối (khoảng ngày sai) không ghi audit xuất tệp
        mvc.perform(get(URL).param("from", day.plusDays(1).toString()).param("to", day.toString())
                .param("doctorId", String.valueOf(doctor.doctorId())).header("Authorization", adminToken()))
                .andExpect(status().isBadRequest());
        assertEquals(1, auditLogs.findByActionCode(AuditActions.REPORT_EXPORT).stream()
                .filter(log -> log.getMetadataJson().replace("\\", "").replace(" ", "").contains(doctorMarker))
                .count());
    }
}
