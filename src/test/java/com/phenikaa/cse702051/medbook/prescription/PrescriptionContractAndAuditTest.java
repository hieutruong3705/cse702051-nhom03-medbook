package com.phenikaa.cse702051.medbook.prescription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
import com.phenikaa.cse702051.medbook.repository.PrescriptionRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-07.5…07.8: hợp đồng JSON của đơn thuốc (chỉ các trường của DTO), mã đơn đúng định dạng và duy nhất, audit
 * đầy đủ cho ghi và đúng một bản cho mỗi yêu cầu đọc, và đường dẫn tra cứu cũ đã bị gỡ.
 */
class PrescriptionContractAndAuditTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ITEM = """
            {"medicineName":"Paracetamol 500mg","dosage":"1 viên","frequency":"3 lần/ngày","durationDays":3,
             "quantity":9,"instructions":"Khi sốt trên 38,5 độ"}
            """;

    @Autowired
    private PrescriptionRepository prescriptions;

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        MvcResult result = actions.andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext();) {
            names.add(it.next());
        }
        return names;
    }

    private long views(long encounterId, long actorUserId) {
        return countAudits(AuditActions.ENTITY_ENCOUNTERS, encounterId, AuditActions.PRESCRIPTION_VIEW, actorUserId);
    }

    @Test
    @DisplayName("AC-07.6 Response chỉ có các trường của DTO; client gửi id, encounterId, mã đơn đều bị bỏ qua")
    void responseHasExactlyTheContractFields() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rxk_fields_doc");
        IsolatedPatient patient = data.isolatedPatient("rxk_fields_pat");
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        Encounter other = data.openEncounter(doctor.doctorId(), patient.patientId());

        JsonNode created = json(send(post("/api/v1/encounters/%d/prescriptions".formatted(encounter.getId())),
                doctor.token(), """
                        {"id":1,"encounterId":%d,"prescriptionCode":"RX-HACK","status":"CANCELLED",
                         "issuedAt":"2000-01-01T00:00:00","notes":"  Uống sau ăn  "}
                        """.formatted(other.getId())));
        assertEquals(Set.of("id", "prescriptionCode", "encounterId", "issuedAt", "notes", "status", "items"),
                fields(created));
        assertEquals(encounter.getId(), created.get("encounterId").asLong(), "lần khám lấy từ đường dẫn");
        assertTrue(created.get("prescriptionCode").asText().startsWith("RX-2"), created.toString());
        assertEquals("ACTIVE", created.get("status").asText());
        assertEquals("Uống sau ăn", created.get("notes").asText());
        assertFalse(created.get("issuedAt").asText().startsWith("2000"));
        assertEquals(0, created.get("items").size());
        long prescriptionId = created.get("id").asLong();

        JsonNode item = json(send(post("/api/v1/prescriptions/%d/items".formatted(prescriptionId)), doctor.token(),
                ITEM.replace("{", "{\"id\":1,\"prescriptionId\":999,\"createdAt\":\"2000-01-01T00:00:00\",")));
        assertEquals(Set.of("id", "medicineId", "medicineName", "dosage", "frequency", "durationDays", "quantity",
                "instructions"), fields(item));
        assertEquals(9, item.get("quantity").asInt());
        assertTrue(item.get("quantity").isIntegralNumber(), "số lượng là số nguyên trong JSON");

        // đọc lại: đơn kèm dòng thuốc, không có trường của entity (createdAt, updatedAt, prescriptionId)
        JsonNode detail = json(mvc.perform(get("/api/v1/prescriptions/" + prescriptionId)
                .header("Authorization", patient.token())));
        assertEquals(Set.of("id", "prescriptionCode", "encounterId", "issuedAt", "notes", "status", "items"),
                fields(detail));
        assertEquals(1, detail.get("items").size());
        assertEquals("Paracetamol 500mg", detail.get("items").get(0).get("medicineName").asText());
        assertEquals(0, json(mvc.perform(get("/api/v1/encounters/%d/prescriptions".formatted(other.getId()))
                .header("Authorization", doctor.token()))).size(), "đơn không bị gắn sang lần khám client chỉ định");

        // sửa đơn: ghi chú và trạng thái đổi được, mã đơn và lần khám thì không
        JsonNode updated = json(send(put("/api/v1/prescriptions/" + prescriptionId), doctor.token(),
                "{\"notes\":\"Ghi chú mới\",\"status\":\"cancelled\",\"prescriptionCode\":\"RX-KHAC\",\"encounterId\":1}"));
        assertEquals("Ghi chú mới", updated.get("notes").asText());
        assertEquals("CANCELLED", updated.get("status").asText());
        assertEquals(created.get("prescriptionCode").asText(), updated.get("prescriptionCode").asText());
        assertEquals(encounter.getId(), updated.get("encounterId").asLong());
    }

    @Test
    @DisplayName("AC-07.8 Mã đơn có dạng RX-yyyyMMdd-XXXXXX (chữ hoa và số) và không trùng khi kê nhiều đơn liên tiếp")
    void prescriptionCodesAreWellFormedAndUnique() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rxk_code_doc");
        IsolatedPatient patient = data.isolatedPatient("rxk_code_pat");
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        String today = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now());

        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            codes.add(json(send(post("/api/v1/encounters/%d/prescriptions".formatted(encounter.getId())),
                    doctor.token(), "{}")).get("prescriptionCode").asText());
        }

        for (String code : codes) {
            assertTrue(code.matches("RX-" + today + "-[A-Z0-9]{6}"), "mã đơn sai định dạng: " + code);
            assertTrue(prescriptions.existsByPrescriptionCode(code));
        }
        assertEquals(codes.size(), new HashSet<>(codes).size(), "mã đơn phải duy nhất: " + codes);
    }

    @Test
    @DisplayName("AC-07.5 Mỗi lần ghi có một bản audit: PRESCRIPTION_CREATE khi kê, PRESCRIPTION_UPDATE khi sửa đơn, thêm/sửa/xóa dòng")
    void everyWriteIsAudited() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rxk_write_doc");
        IsolatedPatient patient = data.isolatedPatient("rxk_write_pat");
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());

        long prescriptionId = json(send(post("/api/v1/encounters/%d/prescriptions".formatted(encounter.getId())),
                doctor.token(), "{\"notes\":\"GHICHU-RX-6611 nội dung lâm sàng\"}")).get("id").asLong();
        assertEquals(1, countAudits(AuditActions.ENTITY_PRESCRIPTIONS, prescriptionId,
                AuditActions.PRESCRIPTION_CREATE, doctor.userId()));

        long itemId = json(send(post("/api/v1/prescriptions/%d/items".formatted(prescriptionId)), doctor.token(), ITEM))
                .get("id").asLong();
        send(put("/api/v1/prescription-items/" + itemId), doctor.token(), "{\"dosage\":\"2 viên\"}")
                .andExpect(status().isOk());
        send(put("/api/v1/prescriptions/" + prescriptionId), doctor.token(), "{\"notes\":\"Sửa ghi chú\"}")
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/prescription-items/" + itemId).header("Authorization", doctor.token()))
                .andExpect(status().isNoContent());

        assertEquals(4, countAudits(AuditActions.ENTITY_PRESCRIPTIONS, prescriptionId,
                AuditActions.PRESCRIPTION_UPDATE, doctor.userId()), "thêm dòng, sửa dòng, sửa đơn, xóa dòng");
        List<String> metadata = auditsOf(AuditActions.ENTITY_PRESCRIPTIONS, prescriptionId).stream()
                .map(AuditLog::getMetadataJson).toList();
        assertEquals(5, metadata.size());
        for (String json : metadata) {
            assertFalse(json.contains("GHICHU-RX-6611"), "audit không được chứa ghi chú của đơn: " + json);
            assertFalse(json.contains("Paracetamol"), "audit không được chứa tên thuốc: " + json);
            assertFalse(json.contains("viên"), "audit không được chứa liều dùng: " + json);
        }
        assertTrue(metadata.stream().anyMatch(json -> json.contains("ITEM_ADDED")));
        assertTrue(metadata.stream().anyMatch(json -> json.contains("ITEM_REMOVED")));

        // yêu cầu ghi bị từ chối (dữ liệu sai) không ghi audit thay đổi
        send(post("/api/v1/prescriptions/%d/items".formatted(prescriptionId)), doctor.token(), "{\"quantity\":1}")
                .andExpect(status().isBadRequest());
        assertEquals(5, auditsOf(AuditActions.ENTITY_PRESCRIPTIONS, prescriptionId).size());
    }

    @Test
    @DisplayName("AC-07.5 Mỗi yêu cầu đọc ghi đúng một bản PRESCRIPTION_VIEW gắn với lần khám, dù lần khám có nhiều đơn")
    void everyReadWritesExactlyOneViewAudit() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rxk_view_doc");
        IsolatedPatient patient = data.isolatedPatient("rxk_view_pat");
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        long encounterId = encounter.getId();
        String createUrl = "/api/v1/encounters/%d/prescriptions".formatted(encounterId);
        long first = json(send(post(createUrl), doctor.token(), "{}")).get("id").asLong();
        json(send(post(createUrl), doctor.token(), "{}"));
        json(send(post(createUrl), doctor.token(), "{}"));
        long itemId = json(send(post("/api/v1/prescriptions/%d/items".formatted(first)), doctor.token(), ITEM))
                .get("id").asLong();
        assertEquals(0, views(encounterId, patient.userId()));
        assertEquals(0, views(encounterId, doctor.userId()), "các thao tác ghi không sinh audit đọc");

        mvc.perform(get(createUrl).header("Authorization", patient.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        assertEquals(1, views(encounterId, patient.userId()), "ba đơn nhưng chỉ một bản audit cho yêu cầu");

        mvc.perform(get("/api/v1/prescriptions/" + first).header("Authorization", patient.token()))
                .andExpect(status().isOk());
        assertEquals(2, views(encounterId, patient.userId()));
        mvc.perform(get("/api/v1/prescriptions/%d/items".formatted(first)).header("Authorization", patient.token()))
                .andExpect(status().isOk());
        assertEquals(3, views(encounterId, patient.userId()));
        mvc.perform(get("/api/v1/prescription-items/" + itemId).header("Authorization", doctor.token()))
                .andExpect(status().isOk());
        assertEquals(1, views(encounterId, doctor.userId()));

        AuditLog listView = auditsOf(AuditActions.ENTITY_ENCOUNTERS, encounterId).stream()
                .filter(log -> AuditActions.PRESCRIPTION_VIEW.equals(log.getActionCode())).findFirst().orElseThrow();
        assertTrue(listView.getMetadataJson().replace("\\", "").replace(" ", "").contains("\"prescriptionCount\":3"),
                listView.getMetadataJson());
        assertFalse(listView.getMetadataJson().contains("Paracetamol"));
    }

    @Test
    @DisplayName("AC-07.5 Bệnh nhân khác, bác sĩ khác, Admin đọc đơn → 403 kèm audit ACCESS_DENIED, không có audit đọc")
    void deniedReadsAreAuditedAsAccessDenied() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rxk_deny_doc");
        IsolatedDoctor otherDoctor = data.isolatedDoctor("rxk_deny_other");
        IsolatedPatient patient = data.isolatedPatient("rxk_deny_pat");
        IsolatedPatient stranger = data.isolatedPatient("rxk_deny_stranger");
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        long encounterId = encounter.getId();
        long prescriptionId = json(send(post("/api/v1/encounters/%d/prescriptions".formatted(encounterId)),
                doctor.token(), "{}")).get("id").asLong();

        mvc.perform(get("/api/v1/prescriptions/" + prescriptionId).header("Authorization", stranger.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/encounters/%d/prescriptions".formatted(encounterId))
                .header("Authorization", otherDoctor.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/prescriptions/%d/items".formatted(prescriptionId))
                .header("Authorization", adminToken())).andExpect(status().isForbidden());

        assertEquals(1, countAudits(AuditActions.ENTITY_ENCOUNTERS, encounterId, AuditActions.ACCESS_DENIED,
                stranger.userId()));
        assertEquals(1, countAudits(AuditActions.ENTITY_ENCOUNTERS, encounterId, AuditActions.ACCESS_DENIED,
                otherDoctor.userId()));
        assertEquals(1, countAudits(AuditActions.ENTITY_ENCOUNTERS, encounterId, AuditActions.ACCESS_DENIED,
                ADMIN_USER_ID));
        assertEquals(0, views(encounterId, stranger.userId()));
        assertEquals(0, views(encounterId, ADMIN_USER_ID));
        // không tồn tại → 404, không phải 403 hay 400
        mvc.perform(get("/api/v1/prescriptions/999999").header("Authorization", stranger.token()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/encounters/999999/prescriptions").header("Authorization", doctor.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-07.7 GET /prescription-items/search đã bị gỡ: 404 với mọi vai trò; khách vẫn 401")
    void searchEndpointIsGone() throws Exception {
        for (String token : new String[] { patientToken(), doctorToken(), adminToken() }) {
            mvc.perform(get("/api/v1/prescription-items/search").param("medicineName", "para")
                    .header("Authorization", token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        mvc.perform(get("/api/v1/prescription-items/search").param("medicineName", "para"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/prescription-items/999999").header("Authorization", doctorToken()))
                .andExpect(status().isNotFound());
    }
}
