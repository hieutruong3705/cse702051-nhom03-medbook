package com.phenikaa.cse702051.medbook.prescription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

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
import com.phenikaa.cse702051.medbook.model.Medicine;
import com.phenikaa.cse702051.medbook.model.PrescriptionItem;
import com.phenikaa.cse702051.medbook.repository.MedicineRepository;
import com.phenikaa.cse702051.medbook.repository.PrescriptionItemRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-07.2…07.4: dòng thuốc lấy từ danh mục phải là thuốc đang hoạt động và chép tên tại thời điểm kê; dữ liệu sai
 * bị từ chối với 400 đúng trường và không để lại bản ghi dở.
 */
class PrescriptionCatalogAndValidationTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MedicineRepository medicines;
    @Autowired
    private PrescriptionItemRepository items;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long idOf(ResultActions actions) throws Exception {
        MvcResult result = actions.andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Medicine medicine(String token, String name, String status) {
        return medicines.save(Medicine.builder().code("RXM" + token).name(name).unit("Viên").status(status).build());
    }

    /** Một lần khám đang mở của bác sĩ và bệnh nhân riêng, kèm một đơn thuốc rỗng. */
    private record Open(IsolatedDoctor doctor, IsolatedPatient patient, long encounterId, long prescriptionId) {
        String itemsUrl() {
            return "/api/v1/prescriptions/%d/items".formatted(prescriptionId);
        }
    }

    private Open open(String key) throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("rxc_" + key + "_doc");
        IsolatedPatient patient = data.isolatedPatient("rxc_" + key + "_pat");
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        long prescriptionId = idOf(send(post("/api/v1/encounters/%d/prescriptions".formatted(encounter.getId())),
                doctor.token(), "{}"));
        return new Open(doctor, patient, encounter.getId(), prescriptionId);
    }

    // ---------- thuốc trong danh mục ----------

    @Test
    @DisplayName("AC-07.2 Kê thuốc trong danh mục: lưu medicineId và chép tên từ danh mục (bỏ qua tên client gửi)")
    void catalogMedicineIsSnapshotted() throws Exception {
        String token = unique();
        Medicine medicine = medicine(token, "Cefixime 200mg " + token, "ACTIVE");
        Open open = open("snapshot");

        send(post(open.itemsUrl()), open.doctor().token(), """
                {"medicineId":%d,"medicineName":"Tên giả do client gửi","dosage":"1 viên","frequency":"2 lần/ngày",
                 "durationDays":5,"quantity":10,"instructions":"Uống sau ăn"}
                """.formatted(medicine.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicineId").value(medicine.getId()))
                .andExpect(jsonPath("$.medicineName").value("Cefixime 200mg " + token))
                .andExpect(jsonPath("$.dosage").value("1 viên"))
                .andExpect(jsonPath("$.frequency").value("2 lần/ngày"))
                .andExpect(jsonPath("$.durationDays").value(5))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.instructions").value("Uống sau ăn"));

        // thuốc ngoài danh mục vẫn kê được bằng tên; các trường tùy chọn có thể bỏ trống
        send(post(open.itemsUrl()), open.doctor().token(), "{\"medicineName\":\"  Thuốc bôi ngoài da  \",\"quantity\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicineId").doesNotExist())
                .andExpect(jsonPath("$.medicineName").value("Thuốc bôi ngoài da"))
                .andExpect(jsonPath("$.dosage").doesNotExist())
                .andExpect(jsonPath("$.durationDays").doesNotExist());
        assertEquals(2, items.countByPrescriptionId(open.prescriptionId()));
    }

    @Test
    @DisplayName("AC-07.2 medicineId không tồn tại hoặc INACTIVE → 400 details.medicineId; thiếu cả mã lẫn tên → 400; không lưu dở")
    void rejectsUnknownOrInactiveMedicine() throws Exception {
        String token = unique();
        Medicine inactive = medicine(token, "Đã ngừng " + token, "INACTIVE");
        Open open = open("inactive");
        long before = items.count();

        send(post(open.itemsUrl()), open.doctor().token(), "{\"medicineId\":%d,\"quantity\":1}".formatted(inactive.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.medicineId")
                        .value("Thuốc không tồn tại trong danh mục hoặc đã ngừng sử dụng"));
        send(post(open.itemsUrl()), open.doctor().token(), "{\"medicineId\":999999,\"quantity\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.medicineId").exists());
        send(post(open.itemsUrl()), open.doctor().token(), "{\"quantity\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.medicineName").value("Tên thuốc không được để trống"));
        send(post(open.itemsUrl()), open.doctor().token(), "{\"medicineName\":\"   \",\"quantity\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.medicineName").exists());

        assertEquals(before, items.count(), "yêu cầu bị từ chối không được để lại dòng thuốc nào");
        assertEquals(0, items.countByPrescriptionId(open.prescriptionId()));
    }

    @Test
    @DisplayName("AC-07.3 Không kê lặp một thuốc trong danh mục trong cùng đơn (400 details.medicineId); đơn khác thì được")
    void sameCatalogMedicineOncePerPrescription() throws Exception {
        String token = unique();
        Medicine medicine = medicine(token, "Losartan 50mg " + token, "ACTIVE");
        Medicine another = medicine(token + "B", "Amlodipin 5mg " + token, "ACTIVE");
        Open open = open("dup");
        String body = "{\"medicineId\":%d,\"quantity\":30}".formatted(medicine.getId());

        long first = idOf(send(post(open.itemsUrl()), open.doctor().token(), body));
        send(post(open.itemsUrl()), open.doctor().token(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.medicineId").value("Thuốc này đã có trong đơn"));
        long second = idOf(send(post(open.itemsUrl()), open.doctor().token(),
                "{\"medicineId\":%d,\"quantity\":30}".formatted(another.getId())));
        // sửa dòng thứ hai thành thuốc của dòng thứ nhất cũng bị chặn; sửa giữ nguyên thuốc của chính nó thì được
        send(put("/api/v1/prescription-items/" + second), open.doctor().token(),
                "{\"medicineId\":%d}".formatted(medicine.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.medicineId").exists());
        send(put("/api/v1/prescription-items/" + first), open.doctor().token(),
                "{\"medicineId\":%d,\"quantity\":60}".formatted(medicine.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(60));

        // đơn thuốc khác của cùng lần khám kê lại thuốc đó được
        long otherPrescription = idOf(send(post("/api/v1/encounters/%d/prescriptions".formatted(open.encounterId())),
                open.doctor().token(), "{}"));
        send(post("/api/v1/prescriptions/%d/items".formatted(otherPrescription)), open.doctor().token(), body)
                .andExpect(status().isOk());
        assertEquals(2, items.countByPrescriptionId(open.prescriptionId()));
    }

    @Test
    @DisplayName("AC-07.4 Đổi tên hoặc ngừng dùng thuốc trong danh mục sau khi kê: đơn cũ giữ nguyên tên cũ")
    void oldPrescriptionsKeepTheNameAtPrescribingTime() throws Exception {
        String token = unique();
        Medicine medicine = medicine(token, "Tên lúc kê " + token, "ACTIVE");
        Open open = open("rename");
        long itemId = idOf(send(post(open.itemsUrl()), open.doctor().token(),
                "{\"medicineId\":%d,\"quantity\":5}".formatted(medicine.getId())));

        mvc.perform(put("/api/v1/admin/medicines/" + medicine.getId()).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tên mới sau này %s\",\"status\":\"INACTIVE\"}".formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mvc.perform(get("/api/v1/prescription-items/" + itemId).header("Authorization", open.patient().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicineId").value(medicine.getId()))
                .andExpect(jsonPath("$.medicineName").value("Tên lúc kê " + token));
        mvc.perform(get("/api/v1/encounters/%d/prescriptions".formatted(open.encounterId()))
                .header("Authorization", open.doctor().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[0].medicineName").value("Tên lúc kê " + token));
        // thuốc đã ngừng không kê mới được nữa, và vì đã có trong đơn nên Admin xóa chỉ là ngừng sử dụng
        send(post(open.itemsUrl()), open.doctor().token(), "{\"medicineId\":%d,\"quantity\":1}".formatted(medicine.getId()))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/admin/medicines/" + medicine.getId()).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // ---------- ràng buộc dữ liệu ----------

    @Test
    @DisplayName("AC-07.3 Số lượng: 0, âm, thập phân, lớn hơn 999 → 400 đúng thông báo; 1 và 999 hợp lệ")
    void validatesQuantity() throws Exception {
        Open open = open("qty");
        String url = open.itemsUrl();
        String token = open.doctor().token();

        for (String bad : new String[] { "0", "-3" }) {
            send(post(url), token, "{\"medicineName\":\"A\",\"quantity\":%s}".formatted(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.quantity").value("Số lượng thuốc phải lớn hơn 0"));
        }
        send(post(url), token, "{\"medicineName\":\"A\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.quantity").value("Số lượng thuốc phải lớn hơn 0"));
        send(post(url), token, "{\"medicineName\":\"A\",\"quantity\":1.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.quantity").value("Số lượng thuốc phải là số nguyên"));
        send(post(url), token, "{\"medicineName\":\"A\",\"quantity\":1000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.quantity").value("Số lượng thuốc tối đa 999"));
        send(post(url), token, "{\"medicineName\":\"A\",\"quantity\":\"nhiều\"}").andExpect(status().isBadRequest());
        assertEquals(0, items.countByPrescriptionId(open.prescriptionId()));

        long one = idOf(send(post(url), token, "{\"medicineName\":\"Một viên\",\"quantity\":1}"));
        long max = idOf(send(post(url), token, "{\"medicineName\":\"Tối đa\",\"quantity\":999.0}"));
        assertEquals(0, BigDecimal.ONE.compareTo(items.findById(one).orElseThrow().getQuantity()));
        assertEquals(0, new BigDecimal("999").compareTo(items.findById(max).orElseThrow().getQuantity()));
        // sửa số lượng cũng bị kiểm
        send(put("/api/v1/prescription-items/" + one), token, "{\"quantity\":2.25}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.quantity").exists());
        assertEquals(0, BigDecimal.ONE.compareTo(items.findById(one).orElseThrow().getQuantity()));
    }

    @Test
    @DisplayName("AC-07.3 Số ngày ngoài 1–365, liều dùng/tần suất dài hơn 100, hướng dẫn dài hơn 500, tên dài hơn 200 → 400")
    void validatesDurationAndTextLengths() throws Exception {
        Open open = open("len");
        String url = open.itemsUrl();
        String token = open.doctor().token();
        String base = "{\"medicineName\":\"A\",\"quantity\":1,%s}";

        send(post(url), token, base.formatted("\"durationDays\":0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.durationDays").value("Số ngày sử dụng phải lớn hơn 0"));
        send(post(url), token, base.formatted("\"durationDays\":366"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.durationDays").value("Số ngày sử dụng tối đa 365 ngày"));
        send(post(url), token, base.formatted("\"durationDays\":2.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.durationDays").value("Số ngày sử dụng phải là số nguyên"));
        send(post(url), token, base.formatted("\"dosage\":\"%s\"".formatted("d".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.dosage").value("Liều dùng tối đa 100 ký tự"));
        send(post(url), token, base.formatted("\"frequency\":\"%s\"".formatted("f".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.frequency").exists());
        send(post(url), token, base.formatted("\"instructions\":\"%s\"".formatted("i".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.instructions").exists());
        send(post(url), token, "{\"medicineName\":\"%s\",\"quantity\":1}".formatted("n".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.medicineName").value("Tên thuốc tối đa 200 ký tự"));
        assertEquals(0, items.countByPrescriptionId(open.prescriptionId()));

        // đúng biên thì được: 365 ngày, liều dùng 100 ký tự
        long ok = idOf(send(post(url), token,
                base.formatted("\"durationDays\":365,\"dosage\":\"%s\"".formatted("d".repeat(100)))));
        PrescriptionItem saved = items.findById(ok).orElseThrow();
        assertEquals(365, saved.getDurationDays());
        assertNull(saved.getFrequency());

        // ghi chú đơn thuốc tối đa 500 ký tự
        send(put("/api/v1/prescriptions/" + open.prescriptionId()), token,
                "{\"notes\":\"%s\"}".formatted("g".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.notes").exists());
        send(put("/api/v1/prescriptions/" + open.prescriptionId()), token, "{\"status\":\"DONE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").exists());
    }

    @Test
    @DisplayName("AC-07.3 Mỗi đơn tối đa 30 dòng thuốc: dòng thứ 31 → 400, xóa bớt rồi thêm lại được")
    void atMostThirtyItemsPerPrescription() throws Exception {
        Open open = open("max");
        String token = open.doctor().token();
        long last = 0;
        for (int i = 1; i <= 30; i++) {
            last = idOf(send(post(open.itemsUrl()), token, "{\"medicineName\":\"Thuốc %d\",\"quantity\":1}".formatted(i)));
        }

        send(post(open.itemsUrl()), token, "{\"medicineName\":\"Thuốc 31\",\"quantity\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.items").value("Mỗi đơn thuốc tối đa 30 dòng thuốc"));
        assertEquals(30, items.countByPrescriptionId(open.prescriptionId()));

        mvc.perform(delete("/api/v1/prescription-items/" + last).header("Authorization", token))
                .andExpect(status().isNoContent());
        send(post(open.itemsUrl()), token, "{\"medicineName\":\"Thuốc thay thế\",\"quantity\":1}")
                .andExpect(status().isOk());
        MvcResult list = mvc.perform(get(open.itemsUrl()).header("Authorization", token))
                .andExpect(status().isOk()).andReturn();
        JsonNode array = JSON.readTree(list.getResponse().getContentAsString());
        assertEquals(30, array.size());
        assertTrue(array.get(0).get("id").asLong() < array.get(29).get("id").asLong(), "theo thứ tự kê");
    }
}
