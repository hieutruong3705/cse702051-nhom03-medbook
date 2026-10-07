package com.phenikaa.cse702051.medbook.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * Báo cáo thứ hai và thứ ba của Admin (sổ tay Buổi 7: tối thiểu ba báo cáo tổng hợp, xuất được tệp): xuất CSV báo
 * cáo doanh thu và báo cáo dịch vụ khám. Số liệu được đối chiếu với các hóa đơn dựng tay trong một ngày riêng.
 */
class RevenueExportAndServiceReportTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REPORTS = "/api/v1/admin/reports";
    private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    @Autowired
    private InvoiceRepository invoices;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private long newService(String code, String name, String price) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/medical-services").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\",\"name\":\"%s\",\"durationMinutes\":30,\"price\":%s}"
                        .formatted(code, name, price)))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    /** Lập hóa đơn qua API thật rồi dời ngày lập về {@code day} để báo cáo của test không lẫn dữ liệu khác. */
    private long invoice(IsolatedDoctor doctor, IsolatedPatient patient, LocalDate day, String linesJson)
            throws Exception {
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        MvcResult result = mvc.perform(post("/api/v1/encounters/%d/invoice".formatted(encounter.getId()))
                .header("Authorization", doctor.token()).contentType(MediaType.APPLICATION_JSON)
                .content(linesJson)).andExpect(status().isOk()).andReturn();
        long id = JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        Invoice invoice = invoices.findById(id).orElseThrow();
        invoice.setIssuedAt(day.atTime(10, 0));
        invoices.save(invoice);
        return id;
    }

    private JsonNode adminJson(String url, LocalDate day) throws Exception {
        MvcResult result = mvc.perform(get(url).param("from", day.toString()).param("to", day.toString())
                .header("Authorization", adminToken())).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static void assertMoney(String expected, JsonNode value) {
        assertEquals(0, new BigDecimal(expected).compareTo(value.decimalValue()), "giá trị: " + value);
    }

    /** Ba hóa đơn trong một ngày: A = 2×X, B = X + Y, C = 3×Y rồi bị hủy. X giá 100.000, Y giá 250.000. */
    private String[] seedDay(LocalDate day) throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rpt" + key.toLowerCase());
        IsolatedPatient patient = data.isolatedPatient("rpt" + key.toLowerCase());
        String codeX = "RX" + key;
        String codeY = "RY" + key;
        long x = newService(codeX, "Siêu âm, tổng quát " + key, "100000");
        long y = newService(codeY, "Xét nghiệm máu " + key, "250000");
        invoice(doctor, patient, day, "[{\"serviceId\":%d,\"quantity\":2}]".formatted(x));
        invoice(doctor, patient, day, "[{\"serviceId\":%d,\"quantity\":1},{\"serviceId\":%d,\"quantity\":1}]"
                .formatted(x, y));
        long voided = invoice(doctor, patient, day, "[{\"serviceId\":%d,\"quantity\":3}]".formatted(y));
        mvc.perform(patch("/api/v1/admin/invoices/%d/void".formatted(voided)).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Lập nhầm dịch vụ\"}"))
                .andExpect(status().isOk());
        return new String[] { codeX, codeY, key };
    }

    @Test
    @DisplayName("Báo cáo dịch vụ khám: số hóa đơn, số lượng, thành tiền, tỷ trọng đúng với đếm tay; không tính hóa đơn đã hủy")
    void serviceReportMatchesManualCount() throws Exception {
        LocalDate day = LocalDate.of(2093, 3, 14);
        String[] codes = seedDay(day);

        JsonNode report = adminJson(REPORTS + "/services", day);
        assertEquals(day.toString(), report.get("from").asText());
        assertMoney("4", report.get("totalQuantity"));
        assertMoney("550000", report.get("totalAmount"));
        assertEquals(2, report.get("services").size());

        JsonNode first = report.get("services").get(0); // giá trị lớn nhất trước
        assertEquals(codes[0], first.get("serviceCode").asText());
        assertEquals("Siêu âm, tổng quát " + codes[2], first.get("serviceName").asText());
        assertEquals(2, first.get("invoiceCount").asInt());
        assertMoney("3", first.get("quantity"));
        assertMoney("300000", first.get("amount"));
        assertMoney("54.55", first.get("sharePercent"));

        JsonNode second = report.get("services").get(1);
        assertEquals(codes[1], second.get("serviceCode").asText());
        assertEquals(1, second.get("invoiceCount").asInt());
        assertMoney("1", second.get("quantity"));
        assertMoney("250000", second.get("amount"));
        assertMoney("45.45", second.get("sharePercent"));

        // ngày không có hóa đơn: báo cáo rỗng, không chia cho 0
        JsonNode empty = adminJson(REPORTS + "/services", day.plusDays(1));
        assertEquals(0, empty.get("services").size());
        assertMoney("0", empty.get("totalAmount"));
    }

    @Test
    @DisplayName("Xuất CSV báo cáo dịch vụ: BOM UTF-8, tên có dấu phẩy không làm vỡ cột, có dòng tổng cộng, ghi audit")
    void serviceReportExportsCsv() throws Exception {
        LocalDate day = LocalDate.of(2093, 4, 21);
        String[] codes = seedDay(day);
        long auditsBefore = exportAudits("services");

        MvcResult result = mvc.perform(get(REPORTS + "/services/export").param("from", day.toString())
                .param("to", day.toString()).header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn();

        assertEquals("text/csv;charset=UTF-8", result.getResponse().getContentType());
        assertEquals("attachment; filename=\"medbook-dich-vu-%s_%s.csv\"".formatted(day, day),
                result.getResponse().getHeader("Content-Disposition"));
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertArrayEquals(UTF8_BOM, Arrays.copyOf(bytes, 3));
        List<String> lines = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).lines().toList();
        assertEquals(4, lines.size(), "tiêu đề + 2 dịch vụ + tổng cộng");
        assertEquals("\"Mã dịch vụ\",\"Tên dịch vụ\",\"Số hóa đơn\",\"Số lượng\",\"Thành tiền\",\"Tỷ trọng (%)\"",
                lines.get(0));
        assertEquals("\"%s\",\"Siêu âm, tổng quát %s\",2,3.00,300000.00,54.55".formatted(codes[0], codes[2]),
                lines.get(1));
        assertEquals("\"%s\",\"Xét nghiệm máu %s\",1,1.00,250000.00,45.45".formatted(codes[1], codes[2]),
                lines.get(2));
        assertEquals("\"\",\"Tổng cộng\",\"\",4.00,550000.00,\"\"", lines.get(3));
        assertEquals(auditsBefore + 1, exportAudits("services"));
    }

    @Test
    @DisplayName("Xuất CSV báo cáo doanh thu: một dòng mỗi ngày kèm tổng cộng, khớp báo cáo JSON; theo tháng và NONE cũng được")
    void revenueReportExportsCsv() throws Exception {
        LocalDate day = LocalDate.of(2093, 6, 9);
        seedDay(day);
        long auditsBefore = exportAudits("revenue");

        JsonNode report = adminJson(REPORTS + "/revenue", day);
        assertMoney("550000", report.get("invoicedAmount"));
        assertEquals(1, report.get("voidCount").asInt());

        MvcResult result = mvc.perform(get(REPORTS + "/revenue/export").param("from", day.toString())
                .param("to", day.toString()).header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn();
        assertEquals("attachment; filename=\"medbook-doanh-thu-%s_%s.csv\"".formatted(day, day),
                result.getResponse().getHeader("Content-Disposition"));
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertArrayEquals(UTF8_BOM, Arrays.copyOf(bytes, 3));
        List<String> lines = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).lines().toList();
        assertEquals(List.of(
                "\"Ngày\",\"Giá trị lập hóa đơn\",\"Đã thu\",\"Chưa thu\",\"Số hóa đơn\",\"Số hóa đơn đã hủy\"",
                "\"09/06/2093\",550000.00,0.00,550000.00,2,1",
                "\"Tổng cộng\",550000.00,0.00,550000.00,2,1"), lines);
        assertEquals(auditsBefore + 1, exportAudits("revenue"));

        String monthly = new String(mvc.perform(get(REPORTS + "/revenue/export").param("from", day.toString())
                .param("to", day.toString()).param("groupBy", "month").header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertTrue(monthly.contains("\"Tháng\",") && monthly.contains("\"06/2093\",550000.00"), monthly);

        String totalOnly = new String(mvc.perform(get(REPORTS + "/revenue/export").param("from", day.toString())
                .param("to", day.toString()).param("groupBy", "NONE").header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertEquals(2, totalOnly.lines().count(), "chỉ tiêu đề và dòng tổng cộng");

        mvc.perform(get(REPORTS + "/revenue/export").param("groupBy", "week").header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.groupBy").isNotEmpty());
    }

    @Test
    @DisplayName("Báo cáo và xuất tệp chỉ dành cho Admin: bệnh nhân, bác sĩ 403; chưa đăng nhập 401; khoảng ngày quá dài 400")
    void reportsAreAdminOnly() throws Exception {
        for (String path : new String[] { "/services", "/services/export", "/revenue/export" }) {
            mvc.perform(get(REPORTS + path)).andExpect(status().isUnauthorized());
            mvc.perform(get(REPORTS + path).header("Authorization", patientToken()))
                    .andExpect(status().isForbidden());
            mvc.perform(get(REPORTS + path).header("Authorization", doctorToken()))
                    .andExpect(status().isForbidden());
            mvc.perform(get(REPORTS + path).header("Authorization", adminToken())).andExpect(status().isOk());
            mvc.perform(get(REPORTS + path).param("from", "2026-01-01").param("to", "2027-06-01")
                    .header("Authorization", adminToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.to").isNotEmpty());
        }
    }

    private long exportAudits(String report) {
        return auditLogs.findAll().stream()
                .filter(audit -> AuditActions.REPORT_EXPORT.equals(audit.getActionCode()))
                .filter(audit -> audit.getMetadataJson() != null
                        && audit.getMetadataJson().replace("\\", "").replace(" ", "")
                                .contains("\"report\":\"" + report + "\""))
                .count();
    }
}
