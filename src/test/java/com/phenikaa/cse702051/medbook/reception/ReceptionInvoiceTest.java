package com.phenikaa.cse702051.medbook.reception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedReceptionist;

/**
 * Hóa đơn tại quầy lễ tân. Tìm hóa đơn, xem các dòng, ghi nhận đã thu (chỉ từ UNPAID, hai yêu cầu đồng thời
 * chỉ một thành công); không hủy được; chỉ lễ tân gọi được.
 */
class ReceptionInvoiceTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "/api/v1/reception/invoices";
    private static final String ADMIN = "/api/v1/admin/invoices";

    @Autowired
    private InvoiceRepository invoices;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    /** Hóa đơn UNPAID lập qua API thật bởi bác sĩ phụ trách một lần khám mới. */
    private long issue(IsolatedDoctor doctor, IsolatedPatient patient) throws Exception {
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        return json(mvc.perform(post("/api/v1/encounters/%d/invoice".formatted(encounter.getId()))
                .header("Authorization", doctor.token()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"serviceId\":1,\"quantity\":2}]")).andExpect(status().isOk())).get("id").asLong();
    }

    /** Hóa đơn dựng thẳng vào CSDL với ngày lập và trạng thái tùy ý (cho test bộ lọc). */
    private Invoice stored(long patientId, String status, String total, LocalDateTime issuedAt) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceCode("RCP-" + unique().toUpperCase());
        invoice.setPatientId(patientId);
        invoice.setSubtotal(new BigDecimal(total));
        invoice.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setStatus(status);
        invoice.setIssuedAt(issuedAt);
        invoice.setPaidAt("PAID".equals(status) ? issuedAt.plusHours(1) : null);
        return invoices.save(invoice);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions list(String... params) throws Exception {
        var request = get(URL).header("Authorization", receptionistToken());
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    private ResultActions collect(String token, long id) throws Exception {
        return mvc.perform(patch(URL + "/" + id + "/collect").header("Authorization", token));
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    // ---------- danh sách ----------

    @Test
    @DisplayName("Danh sách lọc theo trạng thái, khoảng ngày lập, từ khóa; mới nhất trước; không có lý do hủy")
    void listFiltersAndHidesVoidReason() throws Exception {
        String key = unique();
        IsolatedPatient patient = data.isolatedPatient("rinv" + key);
        LocalDateTime base = LocalDate.of(2031, 3, 10).atTime(9, 0);
        Invoice oldUnpaid = stored(patient.patientId(), "UNPAID", "100000.00", base);
        Invoice paid = stored(patient.patientId(), "PAID", "250000.00", base.plusDays(1));
        Invoice newUnpaid = stored(patient.patientId(), "UNPAID", "300000.00", base.plusDays(2));
        Invoice voided = stored(patient.patientId(), "VOID", "50000.00", base.plusDays(3));
        voided.setVoidedAt(base.plusDays(3).plusHours(2));
        voided.setVoidReason("ly-do-huy-" + key);
        invoices.save(voided);
        String name = "rinv" + key;

        JsonNode all = json(list("keyword", name).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].voidReason").doesNotExist())
                .andExpect(jsonPath("$.content[0].patientName").isNotEmpty())
                .andExpect(jsonPath("$.content[0].patientCode").value("ISOP-rinv" + key)));
        assertEquals(List.of(voided.getId(), newUnpaid.getId(), paid.getId(), oldUnpaid.getId()), ids(all));
        assertFalse(all.toString().contains("ly-do-huy-"), "lý do hủy không tới lễ tân");
        assertEquals("VOID", all.get("content").get(0).get("status").asText());
        assertNotNull(all.get("content").get(0).get("voidedAt"));

        assertEquals(List.of(newUnpaid.getId(), oldUnpaid.getId()),
                ids(json(list("keyword", name, "status", "UNPAID"))));
        assertEquals(List.of(paid.getId()), ids(json(list("keyword", name, "status", "paid"))));
        assertEquals(List.of(newUnpaid.getId(), paid.getId()),
                ids(json(list("keyword", name, "from", "2031-03-11", "to", "2031-03-12"))));
        assertEquals(List.of(paid.getId()), ids(json(list("keyword", paid.getInvoiceCode().toLowerCase()))));
        assertEquals(List.of(), ids(json(list("keyword", "khong-co-" + key))));

        list("from", "2031-03-12", "to", "2031-03-11").andExpect(status().isBadRequest());
        list("status", "KHONG_CO").andExpect(status().isBadRequest());
        list("size", "1000", "page", "-3").andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.page").value(0));
    }

    // ---------- dòng hóa đơn ----------

    @Test
    @DisplayName("Dòng hóa đơn trả đủ tên dịch vụ, số lượng, đơn giá, thành tiền; hóa đơn không tồn tại 404")
    void listsInvoiceItems() throws Exception {
        String key = unique();
        long id = issue(data.isolatedDoctor("rinvi" + key), data.isolatedPatient("rinvi" + key));

        mvc.perform(get(URL + "/" + id + "/items").header("Authorization", receptionistToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceId").value(1))
                .andExpect(jsonPath("$[0].description").isNotEmpty())
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[0].unitPrice").isNumber())
                .andExpect(jsonPath("$[0].lineTotal").isNumber());
        JsonNode item = json(mvc.perform(get(URL + "/" + id + "/items")
                .header("Authorization", receptionistToken()))).get(0);
        assertEquals(0, item.get("unitPrice").decimalValue().multiply(BigDecimal.valueOf(2))
                .compareTo(item.get("lineTotal").decimalValue()));

        mvc.perform(get(URL + "/999999999/items").header("Authorization", receptionistToken()))
                .andExpect(status().isNotFound());
    }

    // ---------- thu tiền ----------

    @Test
    @DisplayName("Thu hóa đơn UNPAID: 200, PAID, có paidAt, một bản nhật ký của lễ tân; thu lần hai 409")
    void collectsUnpaidInvoiceOnce() throws Exception {
        String key = unique();
        IsolatedReceptionist receptionist = data.isolatedReceptionist("rinvc" + key);
        long id = issue(data.isolatedDoctor("rinvc" + key), data.isolatedPatient("rinvc" + key));

        collect(receptionist.token(), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty())
                .andExpect(jsonPath("$.patientCode").value("ISOP-rinvc" + key))
                .andExpect(jsonPath("$.voidReason").doesNotExist());
        LocalDateTime paidAt = invoices.findById(id).orElseThrow().getPaidAt();
        assertNotNull(paidAt);
        assertEquals(1, countAudits(AuditActions.ENTITY_INVOICES, id, AuditActions.INVOICE_COLLECT,
                receptionist.userId()));
        assertTrue(auditsOf(AuditActions.ENTITY_INVOICES, id).stream()
                .anyMatch(a -> a.getActionCode().equals(AuditActions.INVOICE_COLLECT)
                        && a.getMetadataJson().contains("totalAmount")));

        collect(receptionist.token(), id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertEquals(paidAt, invoices.findById(id).orElseThrow().getPaidAt(),
                "yêu cầu bị từ chối không được đổi thời điểm thu");
        assertEquals(1, countAudits(AuditActions.ENTITY_INVOICES, id, AuditActions.INVOICE_COLLECT,
                receptionist.userId()));
    }

    @Test
    @DisplayName("Thu hóa đơn đã hủy 409; hóa đơn không tồn tại 404")
    void collectRejectsVoidedOrUnknownInvoices() throws Exception {
        String key = unique();
        long id = issue(data.isolatedDoctor("rinvv" + key), data.isolatedPatient("rinvv" + key));
        mvc.perform(patch(ADMIN + "/" + id + "/void").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Lập nhầm\"}"))
                .andExpect(status().isOk());

        collect(receptionistToken(), id).andExpect(status().isConflict());
        Invoice after = invoices.findById(id).orElseThrow();
        assertEquals("VOID", after.getStatus());
        assertNull(after.getPaidAt());

        collect(receptionistToken(), 999_999_999L).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Các yêu cầu thu đồng thời cùng một hóa đơn: đúng một 200, còn lại 409, một bản nhật ký")
    void concurrentCollectionsOnlyOneSucceeds() throws Exception {
        String key = unique();
        long id = issue(data.isolatedDoctor("rinvr" + key), data.isolatedPatient("rinvr" + key));
        String deskOne = receptionistToken();
        String deskTwo = data.isolatedReceptionist("rinvr" + key).token();
        int requests = 12;

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String token = i % 2 == 0 ? deskOne : deskTwo;
            Callable<Integer> task = () -> {
                ready.countDown();
                go.await();
                return collect(token, id).andReturn().getResponse().getStatus();
            };
            futures.add(pool.submit(task));
        }
        assertTrue(ready.await(30, TimeUnit.SECONDS));
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "chỉ một yêu cầu thu được: " + statuses);
        assertEquals(requests - 1, statuses.stream().filter(s -> s == 409).count(), statuses.toString());
        assertEquals("PAID", invoices.findById(id).orElseThrow().getStatus());
        assertEquals(1, auditsOf(AuditActions.ENTITY_INVOICES, id).stream()
                .filter(a -> a.getActionCode().equals(AuditActions.INVOICE_COLLECT)).count());
    }

    // ---------- quyền ----------

    @Test
    @DisplayName("Lễ tân không dùng được đường thu và hủy của quản trị; quản trị viên vẫn thu và hủy như cũ")
    void receptionistCannotUseAdminInvoiceRoutes() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rinva" + key);
        IsolatedPatient patient = data.isolatedPatient("rinva" + key);
        long first = issue(doctor, patient);
        long second = issue(doctor, patient);

        mvc.perform(patch(ADMIN + "/" + first + "/collect").header("Authorization", receptionistToken()))
                .andExpect(status().isForbidden());
        mvc.perform(patch(ADMIN + "/" + first + "/void").header("Authorization", receptionistToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"thử\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(ADMIN).header("Authorization", receptionistToken())).andExpect(status().isForbidden());
        assertEquals("UNPAID", invoices.findById(first).orElseThrow().getStatus());

        mvc.perform(patch(ADMIN + "/" + first + "/collect").header("Authorization", adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(patch(ADMIN + "/" + second + "/void").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Lập nhầm\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOID"));
    }

    @Test
    @DisplayName("Khách 401; bệnh nhân chủ hóa đơn, bác sĩ phụ trách, quản trị viên đều 403 ở ba điểm cuối hóa đơn của quầy")
    void onlyReceptionistsMayUseTheEndpoints() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rinvp" + key);
        IsolatedPatient patient = data.isolatedPatient("rinvp" + key);
        long id = issue(doctor, patient);

        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL + "/" + id + "/items")).andExpect(status().isUnauthorized());
        mvc.perform(patch(URL + "/" + id + "/collect")).andExpect(status().isUnauthorized());

        for (String token : List.of(patient.token(), doctor.token(), adminToken())) {
            mvc.perform(get(URL).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get(URL + "/" + id + "/items").header("Authorization", token))
                    .andExpect(status().isForbidden());
            collect(token, id).andExpect(status().isForbidden());
        }
        assertEquals("UNPAID", invoices.findById(id).orElseThrow().getStatus());
    }
}
