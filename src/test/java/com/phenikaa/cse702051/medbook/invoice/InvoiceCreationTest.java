package com.phenikaa.cse702051.medbook.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.model.InvoiceItem;
import com.phenikaa.cse702051.medbook.model.MedicalService;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceItemRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-08.1…08.4: lập hóa đơn tính tiền chính xác, đơn giá luôn từ danh mục, dữ liệu sai bị từ chối mà không lưu
 * gì, hai yêu cầu lập cùng lúc chỉ một yêu cầu thành công, và chỉ đúng người mới đọc được hóa đơn.
 */
class InvoiceCreationTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private InvoiceRepository invoices;
    @Autowired
    private InvoiceItemRepository invoiceItems;
    @Autowired
    private MedicalServiceRepository services;
    @Autowired
    private EncounterRepository encounters;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private MedicalService service(String token, String name, String price, String status) {
        return services.save(MedicalService.builder().code("INV" + token).name(name).durationMinutes(15)
                .price(new BigDecimal(price)).status(status).build());
    }

    private ResultActions create(String token, long encounterId, String query, String body) throws Exception {
        return mvc.perform(post("/api/v1/encounters/%d/invoice%s".formatted(encounterId, query))
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        MvcResult result = actions.andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", token));
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext();) {
            names.add(it.next());
        }
        return names;
    }

    // ---------- tính tiền ----------

    @Test
    @DisplayName("AC-08.1 Tính tiền chính xác với số thập phân: 3 × 0,10 + 2 × 199.999,99, giảm 0,05; so sánh BigDecimal đúng từng chữ số")
    void calculatesMoneyExactly() throws Exception {
        String token = unique();
        MedicalService cheap = service(token + "A", "Băng gạc " + token, "0.10", "ACTIVE");
        MedicalService costly = service(token + "B", "Chụp cộng hưởng từ " + token, "199999.99", "ACTIVE");
        IsolatedDoctor doctor = data.isolatedDoctor("inv_money_doc");
        IsolatedPatient patient = data.isolatedPatient("inv_money_pat");
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());

        MvcResult result = create(doctor.token(), encounter.getId(), "?discountAmount=0.05",
                "[{\"serviceId\":%d,\"quantity\":3},{\"serviceId\":%d,\"quantity\":2}]"
                        .formatted(cheap.getId(), costly.getId()))
                .andExpect(status().isOk()).andReturn();
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        long invoiceId = JSON.readTree(raw).get("id").asLong();

        Invoice saved = invoices.findById(invoiceId).orElseThrow();
        assertEquals(new BigDecimal("400000.28"), saved.getSubtotal(), "0,30 + 399.999,98");
        assertEquals(new BigDecimal("0.05"), saved.getDiscountAmount());
        assertEquals(new BigDecimal("400000.23"), saved.getTotalAmount());
        List<InvoiceItem> lines = invoiceItems.findByInvoiceIdOrderByIdAsc(invoiceId);
        assertEquals(2, lines.size());
        assertEquals(new BigDecimal("0.10"), lines.get(0).getUnitPrice());
        assertEquals(new BigDecimal("0.30"), lines.get(0).getLineTotal(), "3 × 0,10 phải đúng 0,30");
        assertEquals(new BigDecimal("399999.98"), lines.get(1).getLineTotal());
        assertTrue(raw.contains("\"subtotal\":400000.28"), raw);
        assertTrue(raw.contains("\"totalAmount\":400000.23"), raw);
        assertTrue(raw.contains("\"lineTotal\":0.30"), raw);
    }

    @Test
    @DisplayName("AC-08.1, AC-08.10 Hóa đơn trả đúng các trường của DTO; đơn giá, tên dịch vụ, mã và bệnh nhân do server quyết định")
    void responseFollowsContractAndIgnoresClientPrices() throws Exception {
        String token = unique();
        MedicalService service = service(token, "Điện tim " + token, "120000", "ACTIVE");
        IsolatedDoctor doctor = data.isolatedDoctor("inv_dto_doc");
        IsolatedPatient patient = data.isolatedPatient("inv_dto_pat");
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());

        // giảm giá đúng bằng tạm tính → tổng 0; mọi trường "tự khai" của client bị bỏ qua
        JsonNode invoice = json(create(doctor.token(), encounter.getId(), "?discountAmount=240000", """
                [{"id":1,"invoiceId":1,"serviceId":%d,"quantity":2,"unitPrice":1,"lineTotal":1,
                  "description":"Tên giả do client gửi"}]
                """.formatted(service.getId())));

        assertEquals(Set.of("id", "invoiceCode", "patientId", "patientName", "appointmentId", "encounterId",
                "subtotal", "discountAmount", "totalAmount", "status", "issuedAt", "paidAt", "voidedAt",
                "voidReason", "items"), fields(invoice));
        String today = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now());
        assertTrue(invoice.get("invoiceCode").asText().matches("INV-" + today + "-[A-Z0-9]{6}"),
                invoice.get("invoiceCode").asText());
        assertEquals(patient.patientId(), invoice.get("patientId").asLong());
        assertEquals("Bệnh nhân thử nghiệm inv_dto_pat", invoice.get("patientName").asText());
        assertEquals(encounter.getAppointmentId(), invoice.get("appointmentId").asLong());
        assertEquals(encounter.getId(), invoice.get("encounterId").asLong());
        assertEquals("UNPAID", invoice.get("status").asText());
        assertTrue(invoice.get("paidAt").isNull() && invoice.get("voidedAt").isNull());
        assertEquals(0, new BigDecimal("240000").compareTo(invoice.get("subtotal").decimalValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.get("totalAmount").decimalValue()));

        JsonNode line = invoice.get("items").get(0);
        assertEquals(Set.of("id", "serviceId", "description", "quantity", "unitPrice", "lineTotal"), fields(line));
        assertEquals("Điện tim " + token, line.get("description").asText());
        assertEquals(0, new BigDecimal("120000").compareTo(line.get("unitPrice").decimalValue()));
        assertEquals(0, new BigDecimal("240000").compareTo(line.get("lineTotal").decimalValue()));
        assertEquals(1, invoiceItems.findByInvoiceIdOrderByIdAsc(invoice.get("id").asLong()).size());

        // đổi giá trong danh mục sau khi lập: hóa đơn cũ giữ nguyên đơn giá lúc lập
        service.setPrice(new BigDecimal("999999"));
        services.save(service);
        getAs(patient.token(), "/api/v1/invoices/" + invoice.get("id").asLong())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(120000.0))
                .andExpect(jsonPath("$.subtotal").value(240000.0));
    }

    // ---------- đồng thời ----------

    @Test
    @DisplayName("AC-08.2 Hai yêu cầu lập hóa đơn cùng lúc cho một lần khám: đúng một thành công, một 409, chỉ một hóa đơn")
    void concurrentCreationProducesOneInvoice() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("inv_race_doc");
        IsolatedPatient patient = data.isolatedPatient("inv_race_pat");
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        String body = "[{\"serviceId\":1,\"quantity\":1}]";

        int requests = 8;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            Callable<Integer> task = () -> {
                ready.countDown();
                go.await();
                return create(doctor.token(), encounter.getId(), "", body).andReturn().getResponse().getStatus();
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

        assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "đúng một yêu cầu lập được: " + statuses);
        assertEquals(requests - 1, statuses.stream().filter(s -> s == 409).count(), "còn lại phải 409: " + statuses);
        assertEquals(1, invoices.findAll().stream()
                .filter(invoice -> encounter.getAppointmentId().equals(invoice.getAppointmentId())).count());
        long invoiceId = invoices.findByAppointmentId(encounter.getAppointmentId()).orElseThrow().getId();
        assertEquals(1, invoiceItems.findByInvoiceIdOrderByIdAsc(invoiceId).size(),
                "yêu cầu thua không được để lại dòng hóa đơn nào");
    }

    // ---------- dữ liệu sai ----------

    @Test
    @DisplayName("AC-08.3 Dòng thiếu serviceId, dịch vụ INACTIVE hoặc không tồn tại, dòng trùng, danh sách rỗng, quá 20 dòng: lỗi đúng mã, không lưu gì")
    void rejectsInvalidLines() throws Exception {
        String token = unique();
        MedicalService inactive = service(token, "Đã ngừng " + token, "50000", "INACTIVE");
        IsolatedDoctor doctor = data.isolatedDoctor("inv_lines_doc");
        IsolatedPatient patient = data.isolatedPatient("inv_lines_pat");
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        long encounterId = encounter.getId();
        long before = invoices.count();

        create(doctor.token(), encounterId, "", "[{\"quantity\":1,\"unitPrice\":1000,\"description\":\"Dòng tự do\"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.serviceId").exists());
        create(doctor.token(), encounterId, "", "[{\"serviceId\":%d,\"quantity\":1}]".formatted(inactive.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.serviceId").value("Dịch vụ \"Đã ngừng " + token + "\" đã ngừng sử dụng"));
        create(doctor.token(), encounterId, "", "[{\"serviceId\":999999,\"quantity\":1}]")
                .andExpect(status().isNotFound());
        create(doctor.token(), encounterId, "", "[{\"serviceId\":1,\"quantity\":1},{\"serviceId\":1,\"quantity\":2}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.serviceId").exists());
        create(doctor.token(), encounterId, "", "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.items").value("Hóa đơn phải có ít nhất một dịch vụ"));
        StringBuilder tooMany = new StringBuilder("[");
        for (int i = 0; i < 21; i++) {
            tooMany.append(i == 0 ? "" : ",").append("{\"serviceId\":").append(1000 + i).append(",\"quantity\":1}");
        }
        create(doctor.token(), encounterId, "", tooMany.append("]").toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.items").value("Hóa đơn tối đa 20 dòng"));
        create(doctor.token(), encounterId, "", "{\"serviceId\":1}").andExpect(status().isBadRequest());

        assertEquals(before, invoices.count(), "các yêu cầu sai không được để lại hóa đơn");
        getAs(doctor.token(), "/api/v1/encounters/%d/invoice".formatted(encounterId)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-08.3 Số lượng ngoài 1–100 hoặc thập phân, giảm giá âm, lớn hơn tạm tính hoặc quá hai chữ số thập phân → 400 đúng trường")
    void rejectsInvalidQuantityAndDiscount() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("inv_qty_doc");
        IsolatedPatient patient = data.isolatedPatient("inv_qty_pat");
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        long encounterId = encounter.getId();
        String one = "[{\"serviceId\":1,\"quantity\":%s}]";

        for (String bad : new String[] { "0", "-1", "null" }) {
            create(doctor.token(), encounterId, "", one.formatted(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.quantity").value("Số lượng phải lớn hơn 0"));
        }
        create(doctor.token(), encounterId, "", one.formatted("1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.quantity").value("Số lượng phải là số nguyên"));
        create(doctor.token(), encounterId, "", one.formatted("101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.quantity").value("Số lượng tối đa 100"));

        // DV01 giá 200.000: giảm giá 200.000,01 lớn hơn tạm tính
        create(doctor.token(), encounterId, "?discountAmount=200000.01", one.formatted("1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.discountAmount").value("Giảm giá không được lớn hơn tạm tính"));
        create(doctor.token(), encounterId, "?discountAmount=-1", one.formatted("1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.discountAmount").value("Giảm giá không được âm"));
        create(doctor.token(), encounterId, "?discountAmount=10.005", one.formatted("1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.discountAmount").exists());
        create(doctor.token(), encounterId, "?discountAmount=abc", one.formatted("1"))
                .andExpect(status().isBadRequest());
        getAs(doctor.token(), "/api/v1/encounters/%d/invoice".formatted(encounterId)).andExpect(status().isNotFound());

        // đúng biên: số lượng 100 hợp lệ
        create(doctor.token(), encounterId, "", one.formatted("100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(20000000.0));
    }

    @Test
    @DisplayName("Lập được sau khi hoàn thành khám; lần khám không gắn lịch hẹn → 409; lần khám không tồn tại → 404")
    void encounterStateRules() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("inv_state_doc");
        IsolatedPatient patient = data.isolatedPatient("inv_state_pat");
        Encounter completed = data.openEncounter(doctor.doctorId(), patient.patientId());
        mvc.perform(put("/api/v1/encounters/" + completed.getId()).header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"diagnosis\":\"Đã khám\",\"status\":\"COMPLETED\"}")).andExpect(status().isOk());
        String body = "[{\"serviceId\":1,\"quantity\":1}]";

        create(doctor.token(), completed.getId(), "", body).andExpect(status().isOk());

        Encounter walkIn = data.openEncounter(doctor.doctorId(), patient.patientId());
        walkIn.setAppointmentId(null);
        encounters.save(walkIn);
        create(doctor.token(), walkIn.getId(), "", body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        create(doctor.token(), 999_999L, "", body).andExpect(status().isNotFound());
    }

    // ---------- quyền đọc ----------

    @Test
    @DisplayName("AC-08.4 Đọc hóa đơn: bệnh nhân chủ, bác sĩ phụ trách, Admin được; bệnh nhân khác và bác sĩ khác 403 kèm audit")
    void readAccessRules() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("inv_read_doc");
        IsolatedDoctor otherDoctor = data.isolatedDoctor("inv_read_other");
        IsolatedPatient owner = data.isolatedPatient("inv_read_owner");
        IsolatedPatient stranger = data.isolatedPatient("inv_read_stranger");
        Encounter encounter = data.openEncounter(doctor.doctorId(), owner.patientId());
        long invoiceId = json(create(doctor.token(), encounter.getId(), "", "[{\"serviceId\":1,\"quantity\":1}]"))
                .get("id").asLong();
        String detail = "/api/v1/invoices/" + invoiceId;

        for (String token : new String[] { owner.token(), doctor.token(), adminToken() }) {
            getAs(token, detail).andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(invoiceId))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].description").value("Khám tổng quát"));
            getAs(token, detail + "/items").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        }
        for (IsolatedPatient intruder : List.of(stranger)) {
            getAs(intruder.token(), detail).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            getAs(intruder.token(), detail + "/items").andExpect(status().isForbidden());
        }
        getAs(otherDoctor.token(), detail).andExpect(status().isForbidden());

        assertEquals(2, countAudits(AuditActions.ENTITY_INVOICES, invoiceId, AuditActions.ACCESS_DENIED,
                stranger.userId()));
        assertEquals(1, countAudits(AuditActions.ENTITY_INVOICES, invoiceId, AuditActions.ACCESS_DENIED,
                otherDoctor.userId()));
        // Admin đọc hóa đơn theo mã hóa đơn được, nhưng không đi qua lần khám (dữ liệu khám) được
        getAs(adminToken(), "/api/v1/encounters/%d/invoice".formatted(encounter.getId()))
                .andExpect(status().isForbidden());
        getAs(owner.token(), "/api/v1/invoices/999999").andExpect(status().isNotFound());
        assertFalse(invoices.findById(invoiceId).isEmpty());
    }
}
