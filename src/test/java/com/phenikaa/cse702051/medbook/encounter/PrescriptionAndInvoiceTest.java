package com.phenikaa.cse702051.medbook.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * Kê đơn thuốc và lập hóa đơn trong luồng khám: quyền theo bản ghi (403/404 đúng mã), khóa sau khi hoàn thành khám
 * (409), không ghi đè được bản ghi của người khác bằng {@code id} gửi từ client, đơn giá lấy từ danh mục.
 */
class PrescriptionAndInvoiceTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ITEM = """
            {"medicineName":"Amoxicillin 500mg","dosage":"1 viên","frequency":"3 lần/ngày","durationDays":7,"quantity":21}
            """;

    @Autowired
    private MedicalServiceRepository services;

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", token));
    }

    private long idOf(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long newPrescription(String token, long encounterId) throws Exception {
        return idOf(send(post("/api/v1/encounters/%d/prescriptions".formatted(encounterId)), token, "{\"notes\":\"Sau ăn\"}")
                .andExpect(status().isOk()).andReturn());
    }

    private long newItem(String token, long prescriptionId) throws Exception {
        return idOf(send(post("/api/v1/prescriptions/%d/items".formatted(prescriptionId)), token, ITEM)
                .andExpect(status().isOk()).andReturn());
    }

    private void complete(String token, long encounterId) throws Exception {
        send(put("/api/v1/encounters/" + encounterId), token, "{\"diagnosis\":\"Đã khám\",\"status\":\"COMPLETED\"}")
                .andExpect(status().isOk());
    }

    // ---------- đơn thuốc ----------

    @Test
    @DisplayName("Đơn thuốc: chỉ bác sĩ phụ trách ghi được; bệnh nhân chủ đọc được; người khác 403; không tồn tại 404")
    void prescriptionAccess() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("rx_doc");
        IsolatedDoctor other = data.isolatedDoctor("rx_other");
        IsolatedPatient owner = data.isolatedPatient("rx_owner");
        IsolatedPatient stranger = data.isolatedPatient("rx_stranger");
        Encounter encounter = data.openEncounter(doc.doctorId(), owner.patientId());

        long prescriptionId = newPrescription(doc.token(), encounter.getId());
        long itemId = newItem(doc.token(), prescriptionId);

        // người không phụ trách không kê/thêm/sửa/xóa được
        String createUrl = "/api/v1/encounters/%d/prescriptions".formatted(encounter.getId());
        String itemsUrl = "/api/v1/prescriptions/%d/items".formatted(prescriptionId);
        for (String token : new String[] { owner.token(), other.token(), stranger.token(), adminToken() }) {
            send(post(createUrl), token, "{}").andExpect(status().isForbidden());
            send(post(itemsUrl), token, ITEM).andExpect(status().isForbidden());
            send(put("/api/v1/prescription-items/" + itemId), token, "{\"dosage\":\"2 viên\"}")
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/prescription-items/" + itemId).header("Authorization", token))
                    .andExpect(status().isForbidden());
            send(put("/api/v1/prescriptions/" + prescriptionId), token, "{\"notes\":\"Sửa\"}")
                    .andExpect(status().isForbidden());
        }

        // chủ lần khám đọc được đơn và dòng thuốc; người khác không đọc được
        getAs(owner.token(), createUrl).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        getAs(owner.token(), itemsUrl).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(itemId));
        for (String token : new String[] { other.token(), stranger.token(), adminToken() }) {
            getAs(token, createUrl).andExpect(status().isForbidden());
            getAs(token, itemsUrl).andExpect(status().isForbidden());
            getAs(token, "/api/v1/prescriptions/" + prescriptionId).andExpect(status().isForbidden());
        }

        getAs(doc.token(), "/api/v1/prescriptions/999999").andExpect(status().isNotFound());
        getAs(doc.token(), "/api/v1/prescription-items/999999").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Dòng thuốc sai → 400 với thông báo tiếng Việt có dấu (không bị lỗi mã hóa)")
    void invalidItemMessages() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("rx_valid_doc");
        IsolatedPatient pat = data.isolatedPatient("rx_valid_pat");
        Encounter encounter = data.openEncounter(doc.doctorId(), pat.patientId());
        long prescriptionId = newPrescription(doc.token(), encounter.getId());
        String url = "/api/v1/prescriptions/%d/items".formatted(prescriptionId);

        send(post(url), doc.token(), "{\"quantity\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tên thuốc không được để trống"));
        send(post(url), doc.token(), "{\"medicineName\":\"Paracetamol\",\"quantity\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Số lượng thuốc phải lớn hơn 0"));
        send(post(url), doc.token(), "{\"medicineName\":\"Paracetamol\",\"quantity\":1,\"durationDays\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Số ngày sử dụng phải lớn hơn 0"));
    }

    @Test
    @DisplayName("Client không ghi đè được đơn/dòng thuốc của lần khám khác bằng cách gửi id")
    void clientCannotOverwriteOthersRecords() throws Exception {
        IsolatedDoctor docA = data.isolatedDoctor("rx_over_a");
        IsolatedDoctor docB = data.isolatedDoctor("rx_over_b");
        IsolatedPatient patA = data.isolatedPatient("rx_over_pa");
        IsolatedPatient patB = data.isolatedPatient("rx_over_pb");
        Encounter encA = data.openEncounter(docA.doctorId(), patA.patientId());
        Encounter encB = data.openEncounter(docB.doctorId(), patB.patientId());
        long prescriptionA = newPrescription(docA.token(), encA.getId());
        long itemA = newItem(docA.token(), prescriptionA);

        // B cố tạo đơn/dòng thuốc với id của A: phải sinh bản ghi MỚI, bản của A giữ nguyên
        long prescriptionB = idOf(send(post("/api/v1/encounters/%d/prescriptions".formatted(encB.getId())),
                docB.token(), "{\"id\":%d,\"notes\":\"Đơn của B\"}".formatted(prescriptionA))
                .andExpect(status().isOk()).andReturn());
        assertNotEquals(prescriptionA, prescriptionB);
        long itemB = idOf(send(post("/api/v1/prescriptions/%d/items".formatted(prescriptionB)), docB.token(),
                "{\"id\":%d,\"medicineName\":\"Thuốc của B\",\"quantity\":1}".formatted(itemA))
                .andExpect(status().isOk()).andReturn());
        assertNotEquals(itemA, itemB);

        getAs(docA.token(), "/api/v1/prescriptions/" + prescriptionA)
                .andExpect(status().isOk()).andExpect(jsonPath("$.encounterId").value(encA.getId()))
                .andExpect(jsonPath("$.notes").value("Sau ăn"));
        getAs(docA.token(), "/api/v1/prescription-items/" + itemA)
                .andExpect(status().isOk()).andExpect(jsonPath("$.medicineName").value("Amoxicillin 500mg"));
    }

    @Test
    @DisplayName("Sau khi hoàn thành khám: kê đơn, thêm/sửa/xóa dòng thuốc → 409; vẫn đọc được")
    void prescriptionsLockedAfterCompletion() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("rx_lock_doc");
        IsolatedPatient pat = data.isolatedPatient("rx_lock_pat");
        Encounter encounter = data.openEncounter(doc.doctorId(), pat.patientId());
        long prescriptionId = newPrescription(doc.token(), encounter.getId());
        long itemId = newItem(doc.token(), prescriptionId);

        complete(doc.token(), encounter.getId());

        send(post("/api/v1/encounters/%d/prescriptions".formatted(encounter.getId())), doc.token(), "{}")
                .andExpect(status().isConflict());
        send(post("/api/v1/prescriptions/%d/items".formatted(prescriptionId)), doc.token(), ITEM)
                .andExpect(status().isConflict());
        send(put("/api/v1/prescription-items/" + itemId), doc.token(), "{\"dosage\":\"2 viên\"}")
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/prescription-items/" + itemId).header("Authorization", doc.token()))
                .andExpect(status().isConflict());
        send(put("/api/v1/prescriptions/" + prescriptionId), doc.token(), "{\"notes\":\"Sửa\"}")
                .andExpect(status().isConflict());

        getAs(pat.token(), "/api/v1/prescriptions/%d/items".formatted(prescriptionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Tìm thuốc chỉ trả dòng thuộc đơn mà người gọi được đọc (không lộ đơn của bệnh nhân khác)")
    void searchDoesNotLeakAcrossPatients() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("rx_search_doc");
        IsolatedPatient owner = data.isolatedPatient("rx_search_owner");
        IsolatedPatient stranger = data.isolatedPatient("rx_search_stranger");
        Encounter encounter = data.openEncounter(doc.doctorId(), owner.patientId());
        long prescriptionId = newPrescription(doc.token(), encounter.getId());
        send(post("/api/v1/prescriptions/%d/items".formatted(prescriptionId)), doc.token(),
                "{\"medicineName\":\"Zyrtecxyz 10mg\",\"quantity\":5}").andExpect(status().isOk());

        getAs(owner.token(), "/api/v1/prescription-items/search?medicineName=zyrtecxyz")
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        getAs(stranger.token(), "/api/v1/prescription-items/search?medicineName=zyrtecxyz")
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- hóa đơn ----------

    @Test
    @DisplayName("Hóa đơn: đơn giá lấy từ danh mục (bỏ qua giá client), bệnh nhân chủ xem được, người khác 403, lập lần hai 409")
    void invoiceFlow() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("inv_doc");
        IsolatedDoctor other = data.isolatedDoctor("inv_other");
        IsolatedPatient owner = data.isolatedPatient("inv_owner");
        IsolatedPatient stranger = data.isolatedPatient("inv_stranger");
        Encounter encounter = data.openEncounter(doc.doctorId(), owner.patientId());
        String invoiceUrl = "/api/v1/encounters/%d/invoice".formatted(encounter.getId());
        BigDecimal price = services.findById(1L).orElseThrow().getPrice(); // DV01 Khám tổng quát, 200.000

        // chưa lập → 404 cho người có quyền đọc
        getAs(doc.token(), invoiceUrl).andExpect(status().isNotFound());
        getAs(owner.token(), invoiceUrl).andExpect(status().isNotFound());

        // bệnh nhân/bác sĩ khác không lập được
        String body = "[{\"serviceId\":1,\"quantity\":2,\"unitPrice\":1}]";
        send(post(invoiceUrl), owner.token(), body).andExpect(status().isForbidden());
        send(post(invoiceUrl), other.token(), body).andExpect(status().isForbidden());

        // giá client gửi (1đ) bị bỏ qua: dòng lấy giá và tên từ danh mục; discount tính ở server
        MvcResult created = send(post(invoiceUrl + "?discountAmount=50000"), doc.token(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNPAID"))
                .andReturn();
        JsonNode invoice = JSON.readTree(created.getResponse().getContentAsString());
        BigDecimal expectedSubtotal = price.multiply(BigDecimal.valueOf(2));
        assertEquals(0, expectedSubtotal.compareTo(invoice.get("subtotal").decimalValue()));
        assertEquals(0, expectedSubtotal.subtract(BigDecimal.valueOf(50000))
                .compareTo(invoice.get("totalAmount").decimalValue()));
        long invoiceId = invoice.get("id").asLong();
        getAs(doc.token(), "/api/v1/invoices/%d/items".formatted(invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Khám tổng quát"))
                .andExpect(jsonPath("$[0].serviceId").value(1));
        MvcResult items = getAs(owner.token(), "/api/v1/invoices/%d/items".formatted(invoiceId))
                .andExpect(status().isOk()).andReturn();
        assertEquals(0, price.compareTo(JSON.readTree(items.getResponse().getContentAsString())
                .get(0).get("unitPrice").decimalValue()));

        // lập lần hai cho cùng lần khám → 409
        send(post(invoiceUrl), doc.token(), body).andExpect(status().isConflict());

        // bệnh nhân chủ thấy hóa đơn của mình (qua lần khám và danh sách của tôi)
        getAs(owner.token(), invoiceUrl).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(invoiceId));
        getAs(doc.token(), invoiceUrl).andExpect(status().isOk());
        MvcResult mine = getAs(owner.token(), "/api/v1/invoices/me").andExpect(status().isOk()).andReturn();
        boolean listed = false;
        for (JsonNode node : JSON.readTree(mine.getResponse().getContentAsString())) {
            listed |= node.get("id").asLong() == invoiceId;
        }
        assertTrue(listed);

        // người khác không xem được: 403 (không phải 400); hóa đơn không tồn tại: 404
        for (String token : new String[] { stranger.token(), other.token(), adminToken() }) {
            getAs(token, invoiceUrl).andExpect(status().isForbidden());
        }
        getAs(stranger.token(), "/api/v1/invoices/" + invoiceId).andExpect(status().isForbidden());
        getAs(stranger.token(), "/api/v1/invoices/%d/items".formatted(invoiceId)).andExpect(status().isForbidden());
        getAs(owner.token(), "/api/v1/invoices/999999").andExpect(status().isNotFound());
        getAs(doc.token(), "/api/v1/invoices/me").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Hóa đơn: dịch vụ không tồn tại → 404; dòng thiếu mô tả khi không gắn dịch vụ → 400; id client gửi bị bỏ qua")
    void invoiceValidation() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("inv_val_doc");
        IsolatedPatient pat = data.isolatedPatient("inv_val_pat");
        Encounter encounter = data.openEncounter(doc.doctorId(), pat.patientId());
        String url = "/api/v1/encounters/%d/invoice".formatted(encounter.getId());

        send(post(url), doc.token(), "[{\"serviceId\":999999,\"quantity\":1}]").andExpect(status().isNotFound());
        send(post(url), doc.token(), "[{\"quantity\":1,\"unitPrice\":1000}]").andExpect(status().isBadRequest());
        send(post(url), doc.token(), "[]").andExpect(status().isBadRequest());
        send(post(url + "?discountAmount=-1"), doc.token(), "[{\"serviceId\":1,\"quantity\":1}]")
                .andExpect(status().isBadRequest());
        getAs(doc.token(), url).andExpect(status().isNotFound()); // các yêu cầu sai không để lại hóa đơn

        send(post(url), doc.token(), "[{\"id\":1,\"serviceId\":2,\"quantity\":1}]").andExpect(status().isOk());
        getAs(doc.token(), url).andExpect(status().isOk());
    }
}
