package com.phenikaa.cse702051.medbook.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-08.5…08.10: Admin thu và hủy hóa đơn bằng chuyển trạng thái có điều kiện, danh sách có phân trang, báo cáo
 * doanh thu luôn đối soát được (giá trị lập = đã thu + chưa thu), mỗi thao tác có audit, đường dẫn cũ đã gỡ.
 */
class AdminInvoiceAndRevenueTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ADMIN = "/api/v1/admin/invoices";
    private static final String REVENUE = "/api/v1/admin/reports/revenue";

    @Autowired
    private InvoiceRepository invoices;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    /** Hóa đơn UNPAID lập qua API thật cho một lần khám mới của bác sĩ và bệnh nhân cho trước. */
    private long issue(IsolatedDoctor doctor, IsolatedPatient patient) throws Exception {
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        MvcResult result = mvc.perform(post("/api/v1/encounters/%d/invoice".formatted(encounter.getId()))
                .header("Authorization", doctor.token()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"serviceId\":1,\"quantity\":1}]")).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    /** Hóa đơn dựng thẳng vào CSDL với ngày lập và trạng thái tùy ý (cho test báo cáo và bộ lọc). */
    private Invoice stored(long patientId, String status, String total, LocalDateTime issuedAt) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceCode("TST-" + unique());
        invoice.setPatientId(patientId);
        invoice.setSubtotal(new BigDecimal(total));
        invoice.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setStatus(status);
        invoice.setIssuedAt(issuedAt);
        invoice.setPaidAt("PAID".equals(status) ? issuedAt.plusHours(1) : null);
        return invoices.save(invoice);
    }

    private ResultActions adminPatch(String url, String body) throws Exception {
        return mvc.perform(patch(url).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode adminGet(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    private static BigDecimal money(JsonNode node, String field) {
        return node.get(field).decimalValue().setScale(2);
    }

    // ---------- thu và hủy ----------

    @Test
    @DisplayName("AC-08.5 Thu: UNPAID → PAID và đặt paidAt; thu lần hai 409; hủy hóa đơn đã thu 409")
    void collectsUnpaidInvoiceOnce() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("adminv_collect_doc");
        IsolatedPatient patient = data.isolatedPatient("adminv_collect_pat");
        long id = issue(doctor, patient);

        adminPatch(ADMIN + "/%d/collect".formatted(id), "{\"note\":\"Thu tiền mặt tại quầy\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty())
                .andExpect(jsonPath("$.patientName").value("Bệnh nhân thử nghiệm adminv_collect_pat"))
                .andExpect(jsonPath("$.patientCode").value("ISOP-adminv_collect_pat"));
        LocalDateTime paidAt = invoices.findById(id).orElseThrow().getPaidAt();
        assertNotNull(paidAt);

        adminPatch(ADMIN + "/%d/collect".formatted(id), "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        adminPatch(ADMIN + "/%d/void".formatted(id), "{\"reason\":\"Lập nhầm\"}")
                .andExpect(status().isConflict());
        Invoice after = invoices.findById(id).orElseThrow();
        assertEquals("PAID", after.getStatus());
        assertEquals(paidAt, after.getPaidAt(), "yêu cầu bị từ chối không được đổi thời điểm thu");
        assertNull(after.getVoidedAt());

        // thu không cần body
        long second = issue(doctor, patient);
        mvc.perform(patch(ADMIN + "/%d/collect".formatted(second)).header("Authorization", adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        // bệnh nhân thấy hóa đơn của mình đã được thu
        mvc.perform(get("/api/v1/invoices/" + id).header("Authorization", patient.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("AC-08.5 Hủy: bắt buộc lý do; UNPAID → VOID kèm voidedAt và voidReason; hủy lại và thu sau khi hủy đều 409")
    void voidsUnpaidInvoiceWithReason() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("adminv_void_doc");
        IsolatedPatient patient = data.isolatedPatient("adminv_void_pat");
        long id = issue(doctor, patient);
        String url = ADMIN + "/%d/void".formatted(id);

        adminPatch(url, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.reason").value("Phải nhập lý do hủy hóa đơn"));
        adminPatch(url, "{\"reason\":\"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.reason").exists());
        adminPatch(url, "{\"reason\":\"%s\"}".formatted("x".repeat(256))).andExpect(status().isBadRequest());
        assertEquals("UNPAID", invoices.findById(id).orElseThrow().getStatus());

        adminPatch(url, "{\"reason\":\"  Bệnh nhân không sử dụng dịch vụ  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"))
                .andExpect(jsonPath("$.voidedAt").isNotEmpty())
                .andExpect(jsonPath("$.voidReason").value("Bệnh nhân không sử dụng dịch vụ"))
                .andExpect(jsonPath("$.paidAt").doesNotExist());

        adminPatch(url, "{\"reason\":\"Hủy lần hai\"}").andExpect(status().isConflict());
        adminPatch(ADMIN + "/%d/collect".formatted(id), "{}").andExpect(status().isConflict());
        Invoice after = invoices.findById(id).orElseThrow();
        assertEquals("VOID", after.getStatus());
        assertEquals("Bệnh nhân không sử dụng dịch vụ", after.getVoidReason());
        assertNull(after.getPaidAt());
    }

    @Test
    @DisplayName("AC-08.5 Thu và hủy: hóa đơn không tồn tại 404; bệnh nhân và bác sĩ 403, khách 401; hóa đơn không đổi")
    void onlyAdminCollectsAndVoids() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("adminv_role_doc");
        IsolatedPatient patient = data.isolatedPatient("adminv_role_pat");
        long id = issue(doctor, patient);

        adminPatch(ADMIN + "/999999/collect", "{}").andExpect(status().isNotFound());
        adminPatch(ADMIN + "/999999/void", "{\"reason\":\"Không có\"}").andExpect(status().isNotFound());

        for (String action : new String[] { "collect", "void" }) {
            String url = ADMIN + "/%d/%s".formatted(id, action);
            String body = "{\"reason\":\"Tự hủy\"}";
            mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
            for (String token : new String[] { patient.token(), doctor.token() }) {
                mvc.perform(patch(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andExpect(status().isForbidden());
            }
        }
        mvc.perform(get(ADMIN).header("Authorization", doctor.token())).andExpect(status().isForbidden());
        mvc.perform(get(REVENUE).header("Authorization", patient.token())).andExpect(status().isForbidden());
        assertEquals("UNPAID", invoices.findById(id).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("AC-08.6 Thu và hủy cùng một hóa đơn song song: đúng một yêu cầu thành công, yêu cầu kia 409, trạng thái cuối nhất quán")
    void collectAndVoidRaceHasOneWinner() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("adminv_race_doc");
        IsolatedPatient patient = data.isolatedPatient("adminv_race_pat");
        String admin = adminToken();

        for (int round = 0; round < 5; round++) {
            long id = issue(doctor, patient);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Callable<Integer> collect = () -> {
                ready.countDown();
                go.await();
                return mvc.perform(patch(ADMIN + "/%d/collect".formatted(id)).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
            };
            Callable<Integer> cancel = () -> {
                ready.countDown();
                go.await();
                return mvc.perform(patch(ADMIN + "/%d/void".formatted(id)).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Tranh chấp\"}"))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> collected = pool.submit(collect);
            Future<Integer> voided = pool.submit(cancel);
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            go.countDown();
            int collectStatus = collected.get(60, TimeUnit.SECONDS);
            int voidStatus = voided.get(60, TimeUnit.SECONDS);
            pool.shutdownNow();

            assertTrue((collectStatus == 200 && voidStatus == 409) || (collectStatus == 409 && voidStatus == 200),
                    "đúng một bên thắng: thu=" + collectStatus + ", hủy=" + voidStatus);
            Invoice after = invoices.findById(id).orElseThrow();
            if (collectStatus == 200) {
                assertEquals("PAID", after.getStatus());
                assertNotNull(after.getPaidAt());
                assertNull(after.getVoidedAt());
                assertNull(after.getVoidReason());
            } else {
                assertEquals("VOID", after.getStatus());
                assertNotNull(after.getVoidedAt());
                assertNull(after.getPaidAt());
            }
        }
    }

    // ---------- danh sách ----------

    @Test
    @DisplayName("AC-08.7 Danh sách của bệnh nhân: chỉ hóa đơn của mình, lọc theo trạng thái và ngày lập, có phân trang")
    void patientListsOnlyOwnInvoices() throws Exception {
        IsolatedPatient me = data.isolatedPatient("adminv_mine_me");
        IsolatedPatient other = data.isolatedPatient("adminv_mine_other");
        LocalDateTime base = LocalDateTime.of(2003, 5, 10, 9, 0);
        long unpaid = stored(me.patientId(), "UNPAID", "100000.00", base).getId();
        long paid = stored(me.patientId(), "PAID", "200000.00", base.plusDays(1)).getId();
        long voided = stored(me.patientId(), "VOID", "300000.00", base.plusDays(2)).getId();
        long theirs = stored(other.patientId(), "UNPAID", "999.00", base).getId();

        MvcResult result = mvc.perform(get("/api/v1/invoices/me").header("Authorization", me.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content[0].id").value(voided))
                .andExpect(jsonPath("$.content[0].invoiceCode").isNotEmpty())
                .andExpect(jsonPath("$.content[0].totalAmount").value(300000.0))
                .andExpect(jsonPath("$.content[0].status").value("VOID"))
                .andExpect(jsonPath("$.content[0].issuedAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].patientId").doesNotExist())
                .andReturn();
        JsonNode all = JSON.readTree(result.getResponse().getContentAsString());
        assertEquals(List.of(voided, paid, unpaid), ids(all), "mới nhất trước");
        assertFalse(ids(all).contains(theirs));

        MvcResult byStatus = mvc.perform(get("/api/v1/invoices/me").param("status", "paid")
                .header("Authorization", me.token())).andExpect(status().isOk()).andReturn();
        assertEquals(List.of(paid), ids(JSON.readTree(byStatus.getResponse().getContentAsString())));
        MvcResult byDate = mvc.perform(get("/api/v1/invoices/me").param("from", "2003-05-11").param("to", "2003-05-11")
                .header("Authorization", me.token())).andExpect(status().isOk()).andReturn();
        assertEquals(List.of(paid), ids(JSON.readTree(byDate.getResponse().getContentAsString())));
        MvcResult paged = mvc.perform(get("/api/v1/invoices/me").param("size", "2").param("page", "1")
                .header("Authorization", me.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2)).andReturn();
        assertEquals(List.of(unpaid), ids(JSON.readTree(paged.getResponse().getContentAsString())));

        mvc.perform(get("/api/v1/invoices/me").param("status", "REFUNDED").header("Authorization", me.token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.status").exists());
        mvc.perform(get("/api/v1/invoices/me").header("Authorization", adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-08.7 Danh sách của Admin: lọc theo trạng thái, khoảng ngày lập và từ khóa (mã hóa đơn, tên, mã bệnh nhân), có phân trang")
    void adminListFiltersAndPaginates() throws Exception {
        IsolatedPatient first = data.isolatedPatient("adminv_list_a");
        IsolatedPatient second = data.isolatedPatient("adminv_list_b");
        LocalDateTime base = LocalDateTime.of(2004, 8, 1, 8, 0);
        Invoice early = stored(first.patientId(), "UNPAID", "100000.00", base);
        Invoice middle = stored(first.patientId(), "PAID", "200000.00", base.plusDays(1));
        Invoice late = stored(second.patientId(), "VOID", "300000.00", base.plusDays(2).withHour(23).withMinute(59));
        String from = "2004-08-01";
        String to = "2004-08-03";

        JsonNode all = adminGet(get(ADMIN).param("from", from).param("to", to));
        assertEquals(List.of(late.getId(), middle.getId(), early.getId()), ids(all), "tính trọn ngày cuối, mới nhất trước");
        JsonNode row = all.get("content").get(2);
        assertEquals("Bệnh nhân thử nghiệm adminv_list_a", row.get("patientName").asText());
        assertEquals("ISOP-adminv_list_a", row.get("patientCode").asText());
        assertEquals(early.getInvoiceCode(), row.get("invoiceCode").asText());

        assertEquals(List.of(middle.getId()), ids(adminGet(get(ADMIN).param("from", from).param("to", to)
                .param("status", "PAID"))));
        assertEquals(List.of(early.getId()), ids(adminGet(get(ADMIN).param("from", from).param("to", from))));
        assertEquals(List.of(late.getId()), ids(adminGet(get(ADMIN).param("from", "2004-08-03").param("to", to))));
        // từ khóa: theo mã hóa đơn, theo tên và theo mã bệnh nhân
        assertEquals(List.of(middle.getId()),
                ids(adminGet(get(ADMIN).param("keyword", middle.getInvoiceCode().toLowerCase()))));
        assertEquals(List.of(late.getId()), ids(adminGet(get(ADMIN).param("from", from).param("to", to)
                .param("keyword", "adminv_list_b"))));
        assertEquals(List.of(middle.getId(), early.getId()), ids(adminGet(get(ADMIN).param("from", from)
                .param("to", to).param("keyword", "ISOP-adminv_list_a"))));
        assertEquals(List.of(), ids(adminGet(get(ADMIN).param("from", from).param("to", to).param("keyword", "%"))));

        JsonNode paged = adminGet(get(ADMIN).param("from", from).param("to", to).param("size", "2").param("page", "1"));
        assertEquals(List.of(early.getId()), ids(paged));
        assertEquals(3, paged.get("totalElements").asInt());
        assertEquals(2, paged.get("totalPages").asInt());
        assertEquals(100, adminGet(get(ADMIN).param("size", "1000")).get("size").asInt());

        mvc.perform(get(ADMIN).param("from", to).param("to", from).header("Authorization", adminToken()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.to").exists());
        mvc.perform(get(ADMIN).param("status", "DONE").header("Authorization", adminToken()))
                .andExpect(status().isBadRequest());
    }

    // ---------- doanh thu ----------

    @Test
    @DisplayName("AC-08.8 Doanh thu: giá trị lập = đã thu + chưa thu cho tổng và từng nhóm DAY, MONTH; hóa đơn VOID không vào giá trị lập")
    void revenueReconcilesOverallAndPerGroup() throws Exception {
        IsolatedPatient patient = data.isolatedPatient("adminv_rev_pat");
        long pid = patient.patientId();
        // tháng 3/2001: ngày 10 có PAID 100.000,50 + UNPAID 50.000,25 + VOID 999.999; ngày 20 có PAID 30.000
        stored(pid, "PAID", "100000.50", LocalDateTime.of(2001, 3, 10, 8, 0));
        stored(pid, "UNPAID", "50000.25", LocalDateTime.of(2001, 3, 10, 15, 30));
        stored(pid, "VOID", "999999.00", LocalDateTime.of(2001, 3, 10, 23, 59));
        stored(pid, "PAID", "30000.00", LocalDateTime.of(2001, 3, 20, 0, 0));
        // tháng 4/2001: UNPAID 70.000 + VOID 1.000 + PAID 0,75
        stored(pid, "UNPAID", "70000.00", LocalDateTime.of(2001, 4, 2, 10, 0));
        stored(pid, "VOID", "1000.00", LocalDateTime.of(2001, 4, 2, 11, 0));
        stored(pid, "PAID", "0.75", LocalDateTime.of(2001, 4, 30, 23, 59));
        // ngoài kỳ: không được tính
        stored(pid, "PAID", "5555.00", LocalDateTime.of(2001, 2, 28, 23, 59));
        stored(pid, "PAID", "7777.00", LocalDateTime.of(2001, 5, 1, 0, 0));

        JsonNode total = adminGet(get(REVENUE).param("from", "2001-03-01").param("to", "2001-04-30"));
        assertEquals("NONE", total.get("groupBy").asText());
        assertEquals("2001-03-01", total.get("from").asText());
        assertEquals("2001-04-30", total.get("to").asText());
        assertEquals(new BigDecimal("130001.25"), money(total, "collectedAmount"));
        assertEquals(new BigDecimal("120000.25"), money(total, "unpaidAmount"));
        assertEquals(new BigDecimal("250001.50"), money(total, "invoicedAmount"));
        assertEquals(5, total.get("invoiceCount").asInt());
        assertEquals(2, total.get("voidCount").asInt());
        assertEquals(0, total.get("groups").size());
        assertReconciles(total);

        JsonNode byDay = adminGet(get(REVENUE).param("from", "2001-03-01").param("to", "2001-04-30")
                .param("groupBy", "day"));
        assertEquals("DAY", byDay.get("groupBy").asText());
        assertEquals(4, byDay.get("groups").size(), "10/03, 20/03, 02/04, 30/04");
        JsonNode march10 = byDay.get("groups").get(0);
        assertEquals("2001-03-10", march10.get("key").asText());
        assertEquals("10/03/2001", march10.get("label").asText());
        assertEquals(new BigDecimal("150000.75"), money(march10, "invoicedAmount"));
        assertEquals(new BigDecimal("100000.50"), money(march10, "collectedAmount"));
        assertEquals(new BigDecimal("50000.25"), money(march10, "unpaidAmount"));
        assertEquals(2, march10.get("invoiceCount").asInt());
        assertEquals(1, march10.get("voidCount").asInt());
        assertEquals("2001-04-30", byDay.get("groups").get(3).get("key").asText());
        assertReconciles(byDay);
        assertEquals(money(total, "invoicedAmount"), money(byDay, "invoicedAmount"), "tổng không đổi khi đổi cách nhóm");

        JsonNode byMonth = adminGet(get(REVENUE).param("from", "2001-03-01").param("to", "2001-04-30")
                .param("groupBy", "MONTH"));
        assertEquals(2, byMonth.get("groups").size());
        JsonNode march = byMonth.get("groups").get(0);
        assertEquals("2001-03", march.get("key").asText());
        assertEquals("03/2001", march.get("label").asText());
        assertEquals(new BigDecimal("180000.75"), money(march, "invoicedAmount"));
        assertEquals(new BigDecimal("130000.50"), money(march, "collectedAmount"));
        JsonNode april = byMonth.get("groups").get(1);
        assertEquals(new BigDecimal("70000.75"), money(april, "invoicedAmount"));
        assertEquals(new BigDecimal("0.75"), money(april, "collectedAmount"));
        assertEquals(1, april.get("voidCount").asInt());
        assertReconciles(byMonth);
    }

    private static void assertReconciles(JsonNode report) {
        assertEquals(money(report, "invoicedAmount"),
                money(report, "collectedAmount").add(money(report, "unpaidAmount")),
                "giá trị lập phải bằng đã thu + chưa thu");
        BigDecimal invoiced = BigDecimal.ZERO.setScale(2);
        BigDecimal collected = BigDecimal.ZERO.setScale(2);
        long count = 0;
        long voids = 0;
        for (JsonNode group : report.get("groups")) {
            assertEquals(money(group, "invoicedAmount"),
                    money(group, "collectedAmount").add(money(group, "unpaidAmount")),
                    "bất biến phải đúng cho nhóm " + group.get("key").asText());
            invoiced = invoiced.add(money(group, "invoicedAmount"));
            collected = collected.add(money(group, "collectedAmount"));
            count += group.get("invoiceCount").asLong();
            voids += group.get("voidCount").asLong();
        }
        if (!report.get("groups").isEmpty()) {
            assertEquals(money(report, "invoicedAmount"), invoiced, "tổng các nhóm bằng tổng chung");
            assertEquals(money(report, "collectedAmount"), collected);
            assertEquals(report.get("invoiceCount").asLong(), count);
            assertEquals(report.get("voidCount").asLong(), voids);
        }
    }

    @Test
    @DisplayName("AC-08.8 Doanh thu: khoảng không có hóa đơn trả 0; quá 366 ngày, from sau to, groupBy lạ → 400; mặc định 30 ngày gần nhất")
    void revenueValidatesRangeAndHandlesEmptyPeriods() throws Exception {
        JsonNode empty = adminGet(get(REVENUE).param("from", "1990-01-01").param("to", "1990-12-31")
                .param("groupBy", "MONTH"));
        assertEquals(0, BigDecimal.ZERO.compareTo(empty.get("invoicedAmount").decimalValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(empty.get("collectedAmount").decimalValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(empty.get("unpaidAmount").decimalValue()));
        assertEquals(0, empty.get("invoiceCount").asInt());
        assertEquals(0, empty.get("voidCount").asInt());
        assertEquals(0, empty.get("groups").size());

        mvc.perform(get(REVENUE).param("from", "2001-01-01").param("to", "2002-01-02")
                .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.to").value("Khoảng thời gian tối đa 366 ngày"));
        mvc.perform(get(REVENUE).param("from", "2001-02-01").param("to", "2001-01-01")
                .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.to").exists());
        mvc.perform(get(REVENUE).param("groupBy", "week").header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.groupBy").value("Chỉ nhóm được theo NONE, DAY hoặc MONTH"));

        JsonNode recent = adminGet(get(REVENUE));
        assertEquals(LocalDate.now().minusDays(29).toString(), recent.get("from").asText());
        assertEquals(LocalDate.now().toString(), recent.get("to").asText());
        assertReconciles(recent);
    }

    // ---------- audit và đường dẫn cũ ----------

    @Test
    @DisplayName("AC-08.9 Lập, thu, hủy: mỗi thao tác một bản audit, metadata chỉ có số tiền và mã định danh")
    void everyInvoiceChangeIsAudited() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("adminv_audit_doc");
        IsolatedPatient patient = data.isolatedPatient("adminv_audit_pat");
        long collected = issue(doctor, patient);
        long voided = issue(doctor, patient);

        adminPatch(ADMIN + "/%d/collect".formatted(collected), "{\"note\":\"GHICHU-THU-3301\"}")
                .andExpect(status().isOk());
        adminPatch(ADMIN + "/%d/void".formatted(voided), "{\"reason\":\"LYDO-HUY-3302 bệnh nhân khiếu nại\"}")
                .andExpect(status().isOk());
        // yêu cầu bị từ chối không ghi thêm audit
        adminPatch(ADMIN + "/%d/collect".formatted(collected), "{}").andExpect(status().isConflict());
        adminPatch(ADMIN + "/%d/void".formatted(collected), "{\"reason\":\"Không được\"}")
                .andExpect(status().isConflict());

        assertEquals(1, countAudits(AuditActions.ENTITY_INVOICES, collected, AuditActions.INVOICE_CREATE,
                doctor.userId()));
        assertEquals(1, countAudits(AuditActions.ENTITY_INVOICES, collected, AuditActions.INVOICE_COLLECT,
                ADMIN_USER_ID));
        assertEquals(0, countAudits(AuditActions.ENTITY_INVOICES, collected, AuditActions.INVOICE_VOID,
                ADMIN_USER_ID));
        assertEquals(1, countAudits(AuditActions.ENTITY_INVOICES, voided, AuditActions.INVOICE_VOID, ADMIN_USER_ID));
        assertEquals(2, auditsOf(AuditActions.ENTITY_INVOICES, collected).size());
        assertEquals(2, auditsOf(AuditActions.ENTITY_INVOICES, voided).size());

        List<AuditLog> all = new ArrayList<>(auditsOf(AuditActions.ENTITY_INVOICES, collected));
        all.addAll(auditsOf(AuditActions.ENTITY_INVOICES, voided));
        for (AuditLog log : all) {
            String metadata = log.getMetadataJson().replace("\\", "").replace(" ", "");
            assertTrue(metadata.contains("\"totalAmount\":\"200000.00\""), metadata);
            assertFalse(metadata.contains("GHICHU-THU-3301"), "ghi chú thu không được vào audit: " + metadata);
            assertFalse(metadata.contains("LYDO-HUY-3302"), "lý do hủy không được vào audit: " + metadata);
            assertFalse(metadata.contains("adminv_audit_pat"), "tên bệnh nhân không được vào audit: " + metadata);
            assertFalse(metadata.contains("Khámtổngquát"), "tên dịch vụ không được vào audit: " + metadata);
        }
    }

    @Test
    @DisplayName("AC-08.5, AC-08.10 Đường dẫn PUT /invoices/{id}/pay|void cũ trả 404 và không đổi hóa đơn; JSON không lộ trường của entity")
    void legacyEndpointsAreGone() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("adminv_legacy_doc");
        IsolatedPatient patient = data.isolatedPatient("adminv_legacy_pat");
        long id = issue(doctor, patient);

        for (String action : new String[] { "pay", "void" }) {
            for (String token : new String[] { adminToken(), patient.token(), doctor.token() }) {
                mvc.perform(put("/api/v1/invoices/%d/%s".formatted(id, action)).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isNotFound());
            }
        }
        assertEquals("UNPAID", invoices.findById(id).orElseThrow().getStatus());

        mvc.perform(get("/api/v1/invoices/" + id).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].invoiceId").doesNotExist())
                .andExpect(jsonPath("$.items[0].createdAt").doesNotExist());
        mvc.perform(get(ADMIN).param("keyword", "adminv_legacy_pat").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist());
    }
}
