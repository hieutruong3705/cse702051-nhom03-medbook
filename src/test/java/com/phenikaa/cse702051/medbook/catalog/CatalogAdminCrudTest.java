package com.phenikaa.cse702051.medbook.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Prescription;
import com.phenikaa.cse702051.medbook.model.PrescriptionItem;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.repository.MedicineRepository;
import com.phenikaa.cse702051.medbook.repository.PrescriptionItemRepository;
import com.phenikaa.cse702051.medbook.repository.PrescriptionRepository;
import com.phenikaa.cse702051.medbook.repository.SpecialtyRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * Admin quản lý ba danh mục: tạo, sửa, xóa; mã duy nhất và bất biến; dữ liệu sai báo đúng trường; bản ghi đang
 * được tham chiếu chỉ bị ngừng sử dụng; mỗi thao tác ghi đúng một bản audit.
 */
class CatalogAdminCrudTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private SpecialtyRepository specialties;
    @Autowired
    private MedicalServiceRepository services;
    @Autowired
    private MedicineRepository medicines;
    @Autowired
    private DoctorRepository doctors;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private PrescriptionRepository prescriptions;
    @Autowired
    private PrescriptionItemRepository prescriptionItems;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body) throws Exception {
        return mvc.perform(request.header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode created(String path, String body) throws Exception {
        MvcResult result = send(post("/api/v1/admin/" + path), body).andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions adminGet(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", adminToken()));
    }

    private ResultActions adminDelete(String url) throws Exception {
        return mvc.perform(delete(url).header("Authorization", adminToken()));
    }

    // ---------- chuyên khoa ----------

    @Test
    @DisplayName("AC-01.3 Chuyên khoa: tạo chuẩn hóa mã, mặc định ACTIVE; mã trùng khác hoa thường → 409 details.code")
    void createSpecialtyAndRejectDuplicateCode() throws Exception {
        String token = unique();
        send(post("/api/v1/admin/specialties"),
                "{\"code\":\"  sp-%s  \",\"name\":\"  Khoa mới %s \",\"description\":\" \"}".formatted(
                        token.toLowerCase(), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("SP-" + token))
                .andExpect(jsonPath("$.name").value("Khoa mới " + token))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        send(post("/api/v1/admin/specialties"),
                "{\"code\":\"Sp-%s\",\"name\":\"Trùng mã\"}".formatted(token.toLowerCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.details.code").value("Mã chuyên khoa đã được sử dụng"));
        assertEquals(1, specialties.findAll().stream().filter(s -> s.getCode().equals("SP-" + token)).count());
    }

    @Test
    @DisplayName("AC-01.3 Chuyên khoa: sửa được tên, mô tả, trạng thái; đổi mã → 400 trường code; không tồn tại → 404")
    void updateSpecialtyButNotItsCode() throws Exception {
        String token = unique();
        long id = created("specialties", "{\"code\":\"UP%s\",\"name\":\"Tên cũ\"}".formatted(token)).get("id").asLong();
        LocalDateTime createdAt = specialties.findById(id).orElseThrow().getCreatedAt();

        // gửi lại đúng mã (khác hoa thường) được chấp nhận; không gửi mã cũng được
        send(put("/api/v1/admin/specialties/" + id),
                "{\"code\":\"up%s\",\"name\":\"Tên mới\",\"description\":\"Mô tả mới\",\"status\":\"INACTIVE\"}"
                        .formatted(token.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("UP" + token))
                .andExpect(jsonPath("$.name").value("Tên mới"))
                .andExpect(jsonPath("$.description").value("Mô tả mới"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        send(put("/api/v1/admin/specialties/" + id), "{\"name\":\"Tên mới hơn\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE")); // bỏ trống trạng thái = giữ nguyên
        assertEquals(createdAt, specialties.findById(id).orElseThrow().getCreatedAt(), "createdAt không được đổi");
        assertFalse(specialties.findById(id).orElseThrow().getUpdatedAt().isBefore(createdAt));

        send(put("/api/v1/admin/specialties/" + id), "{\"code\":\"KHAC%s\",\"name\":\"Tên\"}".formatted(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.code").value("Không thể đổi mã sau khi đã tạo"));
        assertEquals("UP" + token, specialties.findById(id).orElseThrow().getCode());

        send(put("/api/v1/admin/specialties/999999"), "{\"name\":\"Không có\"}").andExpect(status().isNotFound());
        adminGet("/api/v1/admin/specialties/999999").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-01.4, AC-01.5 Chuyên khoa: xóa bản chưa tham chiếu → 204 và biến mất; dữ liệu sai → 400 đúng trường")
    void deleteUnreferencedSpecialtyAndValidate() throws Exception {
        String token = unique();
        long id = created("specialties", "{\"code\":\"DL%s\",\"name\":\"Sẽ xóa\"}".formatted(token)).get("id").asLong();

        adminDelete("/api/v1/admin/specialties/" + id).andExpect(status().isNoContent());
        assertTrue(specialties.findById(id).isEmpty());
        adminGet("/api/v1/admin/specialties/" + id).andExpect(status().isNotFound());
        adminDelete("/api/v1/admin/specialties/" + id).andExpect(status().isNotFound());

        send(post("/api/v1/admin/specialties"), "{\"code\":\"OK%s\",\"name\":\"   \"}".formatted(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").value("Tên chuyên khoa không được để trống"));
        send(post("/api/v1/admin/specialties"), "{\"name\":\"Thiếu mã\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.code").value("Mã không được để trống"));
        send(post("/api/v1/admin/specialties"), "{\"code\":\"có dấu cách\",\"name\":\"Mã sai\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.code").exists());
        send(post("/api/v1/admin/specialties"), "{\"code\":\"ST%s\",\"name\":\"Trạng thái lạ\",\"status\":\"DELETED\"}"
                .formatted(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").value("Trạng thái phải là ACTIVE hoặc INACTIVE"));
        send(post("/api/v1/admin/specialties"), "{\"code\":\"LG%s\",\"name\":\"%s\"}".formatted(token, "x".repeat(121)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists());
    }

    // ---------- dịch vụ khám ----------

    @Test
    @DisplayName("AC-01.3 Dịch vụ: tạo với giá scale 2; mã trùng → 409 details.code")
    void createServiceAndRejectDuplicateCode() throws Exception {
        String token = unique();
        long id = created("medical-services", """
                {"code":"sv%s","name":"Nội soi %s","durationMinutes":40,"price":350000.5}
                """.formatted(token.toLowerCase(), token)).get("id").asLong();

        assertEquals(new BigDecimal("350000.50"), services.findById(id).orElseThrow().getPrice());
        adminGet("/api/v1/admin/medical-services/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SV" + token))
                .andExpect(jsonPath("$.durationMinutes").value(40))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        send(post("/api/v1/admin/medical-services"),
                "{\"code\":\"SV%s\",\"name\":\"Trùng\",\"durationMinutes\":10,\"price\":1}".formatted(token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code").value("Mã dịch vụ đã được sử dụng"));
    }

    @Test
    @DisplayName("AC-01.3 Dịch vụ: sửa giá, thời lượng, trạng thái; đổi mã → 400")
    void updateService() throws Exception {
        String token = unique();
        long id = created("medical-services",
                "{\"code\":\"US%s\",\"name\":\"Giá cũ\",\"durationMinutes\":30,\"price\":100000}".formatted(token))
                .get("id").asLong();

        send(put("/api/v1/admin/medical-services/" + id),
                "{\"name\":\"Giá mới\",\"durationMinutes\":45,\"price\":120000,\"status\":\"INACTIVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Giá mới"))
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.price").value(120000.0))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        // đã ngừng sử dụng nên không còn ở trang công khai
        mvc.perform(get("/api/v1/medical-services/" + id)).andExpect(status().isNotFound());

        send(put("/api/v1/admin/medical-services/" + id),
                "{\"code\":\"ZZ%s\",\"name\":\"Giá mới\",\"durationMinutes\":45,\"price\":120000}".formatted(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.code").exists());
        send(put("/api/v1/admin/medical-services/999999"),
                "{\"name\":\"Không có\",\"durationMinutes\":45,\"price\":1}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-01.4, AC-01.5 Dịch vụ: xóa hẳn khi chưa dùng; giá âm, thời lượng ngoài 5–480, giá quá lớn → 400 đúng trường")
    void deleteUnreferencedServiceAndValidate() throws Exception {
        String token = unique();
        long id = created("medical-services",
                "{\"code\":\"DS%s\",\"name\":\"Sẽ xóa\",\"durationMinutes\":30,\"price\":0}".formatted(token))
                .get("id").asLong();
        adminDelete("/api/v1/admin/medical-services/" + id).andExpect(status().isNoContent());
        assertTrue(services.findById(id).isEmpty());

        String base = "{\"code\":\"VS%s\",\"name\":\"Sai\",\"durationMinutes\":%s,\"price\":%s}";
        send(post("/api/v1/admin/medical-services"), base.formatted(token, 30, -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.price").value("Giá không được âm"));
        send(post("/api/v1/admin/medical-services"), base.formatted(token, 4, 1000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.durationMinutes").value("Thời lượng tối thiểu 5 phút"));
        send(post("/api/v1/admin/medical-services"), base.formatted(token, 481, 1000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.durationMinutes").value("Thời lượng tối đa 480 phút"));
        send(post("/api/v1/admin/medical-services"), base.formatted(token, 30, "100000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.price").exists());
        send(post("/api/v1/admin/medical-services"), base.formatted(token, 30, "10.555"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.price").exists());
        send(post("/api/v1/admin/medical-services"), "{\"code\":\"VS%s\",\"name\":\"Thiếu\"}".formatted(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.durationMinutes").exists())
                .andExpect(jsonPath("$.details.price").exists());
        assertFalse(services.existsByCodeIgnoreCase("VS" + token), "yêu cầu sai không được để lại bản ghi");
    }

    // ---------- thuốc ----------

    @Test
    @DisplayName("AC-01.3 Thuốc: tạo; mã trùng → 409 details.code")
    void createMedicineAndRejectDuplicateCode() throws Exception {
        String token = unique();
        created("medicines", "{\"code\":\"md%s\",\"name\":\"Thuốc %s\",\"unit\":\"Viên\",\"description\":\"Hạ sốt\"}"
                .formatted(token.toLowerCase(), token));
        adminGet("/api/v1/admin/medicines?keyword=" + token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("MD" + token))
                .andExpect(jsonPath("$.content[0].unit").value("Viên"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));

        send(post("/api/v1/admin/medicines"), "{\"code\":\"MD%s\",\"name\":\"Trùng\"}".formatted(token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code").value("Mã thuốc đã được sử dụng"));
    }

    @Test
    @DisplayName("AC-01.3 Thuốc: sửa tên, đơn vị, trạng thái; đổi mã → 400")
    void updateMedicine() throws Exception {
        String token = unique();
        long id = created("medicines", "{\"code\":\"UM%s\",\"name\":\"Tên cũ\",\"unit\":\"Viên\"}".formatted(token))
                .get("id").asLong();

        send(put("/api/v1/admin/medicines/" + id), "{\"name\":\"Tên mới\",\"unit\":\"Gói\",\"status\":\"INACTIVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tên mới"))
                .andExpect(jsonPath("$.unit").value("Gói"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        send(put("/api/v1/admin/medicines/" + id), "{\"code\":\"KHAC%s\",\"name\":\"Tên mới\"}".formatted(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.code").exists());
        send(put("/api/v1/admin/medicines/999999"), "{\"name\":\"Không có\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-01.4, AC-01.5 Thuốc: xóa hẳn khi chưa kê; tên rỗng, đơn vị quá dài → 400 đúng trường")
    void deleteUnreferencedMedicineAndValidate() throws Exception {
        String token = unique();
        long id = created("medicines", "{\"code\":\"DM%s\",\"name\":\"Sẽ xóa\"}".formatted(token)).get("id").asLong();
        adminDelete("/api/v1/admin/medicines/" + id).andExpect(status().isNoContent());
        assertTrue(medicines.findById(id).isEmpty());

        send(post("/api/v1/admin/medicines"), "{\"code\":\"VM%s\",\"name\":\"\"}".formatted(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").value("Tên thuốc không được để trống"));
        send(post("/api/v1/admin/medicines"),
                "{\"code\":\"VM%s\",\"name\":\"Thuốc\",\"unit\":\"%s\"}".formatted(token, "u".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.unit").value("Đơn vị tối đa 50 ký tự"));
        send(post("/api/v1/admin/medicines"), "{\"code\":\"%s\",\"name\":\"Mã dài\"}".formatted("M".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.code").value("Mã tối đa 50 ký tự"));
    }

    // ---------- xóa mềm khi đang được tham chiếu ----------

    @Test
    @DisplayName("AC-01.4 Chuyên khoa đang có bác sĩ: DELETE → 200, chuyển INACTIVE, bản ghi và hồ sơ bác sĩ còn nguyên")
    void specialtyInUseIsOnlyDeactivated() throws Exception {
        String token = unique();
        long id = created("specialties", "{\"code\":\"RS%s\",\"name\":\"Đang dùng\"}".formatted(token)).get("id").asLong();
        IsolatedDoctor doctor = data.isolatedDoctor("cat_specialty_" + token);
        Doctor profile = doctors.findById(doctor.doctorId()).orElseThrow();
        profile.setSpecialty(specialties.getReferenceById(id));
        doctors.save(profile);

        adminDelete("/api/v1/admin/specialties/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertEquals("INACTIVE", specialties.findById(id).orElseThrow().getStatus());
        mvc.perform(get("/api/v1/specialties/" + id)).andExpect(status().isNotFound());
        assertTrue(doctors.findById(doctor.doctorId()).isPresent());
    }

    @Test
    @DisplayName("AC-01.4 Dịch vụ đã có lịch hẹn: DELETE → 200, chuyển INACTIVE, bản ghi còn nguyên")
    void serviceInUseIsOnlyDeactivated() throws Exception {
        String token = unique();
        long id = created("medical-services",
                "{\"code\":\"RV%s\",\"name\":\"Đang dùng\",\"durationMinutes\":30,\"price\":50000}".formatted(token))
                .get("id").asLong();
        IsolatedDoctor doctor = data.isolatedDoctor("cat_service_doc");
        IsolatedPatient patient = data.isolatedPatient("cat_service_pat");
        Appointment appointment = data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.BOOKED);
        appointment.setServiceId(id);
        appointments.save(appointment);

        adminDelete("/api/v1/admin/medical-services/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertEquals("INACTIVE", services.findById(id).orElseThrow().getStatus());
        assertEquals(id, appointments.findById(appointment.getId()).orElseThrow().getServiceId());
    }

    @Test
    @DisplayName("AC-01.4 Thuốc đã được kê trong đơn: DELETE → 200, chuyển INACTIVE, bản ghi còn nguyên")
    void medicineInUseIsOnlyDeactivated() throws Exception {
        String token = unique();
        long id = created("medicines", "{\"code\":\"RM%s\",\"name\":\"Đang dùng %s\"}".formatted(token, token))
                .get("id").asLong();
        IsolatedDoctor doctor = data.isolatedDoctor("cat_medicine_doc");
        IsolatedPatient patient = data.isolatedPatient("cat_medicine_pat");
        LocalDateTime now = LocalDateTime.now();
        Prescription prescription = new Prescription();
        prescription.setEncounterId(data.openEncounter(doctor.doctorId(), patient.patientId()).getId());
        prescription.setPrescriptionCode("RX-TEST-" + token);
        prescription.setIssuedAt(now);
        prescription.setStatus("ACTIVE");
        prescription.setCreatedAt(now);
        prescription.setUpdatedAt(now);
        prescription = prescriptions.save(prescription);
        PrescriptionItem item = new PrescriptionItem();
        item.setPrescriptionId(prescription.getId());
        item.setMedicineId(id);
        item.setMedicineName("Đang dùng " + token);
        item.setQuantity(BigDecimal.ONE);
        item.setCreatedAt(now);
        prescriptionItems.save(item);

        adminDelete("/api/v1/admin/medicines/" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertEquals("INACTIVE", medicines.findById(id).orElseThrow().getStatus());
        assertTrue(prescriptionItems.existsByMedicineId(id));
    }

    // ---------- audit ----------

    @Test
    @DisplayName("AC-01.7 Tạo, sửa, xóa danh mục: mỗi thao tác ghi đúng một bản audit với entityType và entityId đúng")
    void everyAdminChangeIsAudited() throws Exception {
        String token = unique();
        long specialtyId = created("specialties", "{\"code\":\"AU%s\",\"name\":\"Audit\"}".formatted(token))
                .get("id").asLong();
        send(put("/api/v1/admin/specialties/" + specialtyId), "{\"name\":\"Audit sửa\"}").andExpect(status().isOk());
        adminDelete("/api/v1/admin/specialties/" + specialtyId).andExpect(status().isNoContent());

        assertEquals(1, countAudits(AuditActions.ENTITY_SPECIALTIES, specialtyId, AuditActions.CATALOG_CREATE,
                ADMIN_USER_ID));
        assertEquals(1, countAudits(AuditActions.ENTITY_SPECIALTIES, specialtyId, AuditActions.CATALOG_UPDATE,
                ADMIN_USER_ID));
        assertEquals(1, countAudits(AuditActions.ENTITY_SPECIALTIES, specialtyId, AuditActions.CATALOG_DELETE,
                ADMIN_USER_ID));
        assertEquals(3, auditsOf(AuditActions.ENTITY_SPECIALTIES, specialtyId).size());
        assertTrue(auditsOf(AuditActions.ENTITY_SPECIALTIES, specialtyId).getLast().getMetadataJson()
                .contains("\"softDeleted\":false"));

        long serviceId = created("medical-services",
                "{\"code\":\"AU%s\",\"name\":\"Audit\",\"durationMinutes\":30,\"price\":1}".formatted(token))
                .get("id").asLong();
        long medicineId = created("medicines", "{\"code\":\"AU%s\",\"name\":\"Audit\"}".formatted(token))
                .get("id").asLong();
        assertEquals(1, countAudits(AuditActions.ENTITY_SERVICES, serviceId, AuditActions.CATALOG_CREATE,
                ADMIN_USER_ID));
        assertEquals(1, countAudits(AuditActions.ENTITY_MEDICINES, medicineId, AuditActions.CATALOG_CREATE,
                ADMIN_USER_ID));

        // yêu cầu bị từ chối (mã trùng) không ghi thêm bản audit nào
        send(post("/api/v1/admin/medicines"), "{\"code\":\"AU%s\",\"name\":\"Trùng\"}".formatted(token))
                .andExpect(status().isConflict());
        assertEquals(1, auditsOf(AuditActions.ENTITY_MEDICINES, medicineId).size());
    }
}
