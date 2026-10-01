package com.phenikaa.cse702051.medbook.patient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.MedicalRecordRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData;

/**
 * BE-02: hồ sơ bệnh nhân — chỉ xem/sửa của chính mình, bác sĩ theo phạm vi, Admin chỉ trường
 * hành chính; đăng ký tạo cả hồ sơ bệnh nhân lẫn bệnh án.
 */
class PatientAccessTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void fixtures() {
        data.appointment(ApiTestData.DOCTOR1_ID, ApiTestData.PATIENT1_ID, AppointmentStatus.BOOKED);
        data.appointment(ApiTestData.DOCTOR2_ID, ApiTestData.PATIENT2_ID, AppointmentStatus.BOOKED);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    // ---------- /patients/me ----------

    @Test
    @DisplayName("GET /patients/me trả đúng hồ sơ của người đăng nhập, không phải của người khác")
    void meReturnsOwnProfile() throws Exception {
        mvc.perform(get("/api/v1/patients/me").header("Authorization", patientToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCode").value("BN000001"))
                .andExpect(jsonPath("$.userId").value((int) PATIENT1_USER_ID));
        mvc.perform(get("/api/v1/patients/me").header("Authorization", patient2Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCode").value("BN000002"));
    }

    @Test
    @DisplayName("PUT /patients/me cập nhật được các trường cho phép và bỏ qua patientCode/status/userId")
    void updateOwnProfileIgnoresProtectedFields() throws Exception {
        String payload = """
                {"address":"Số 1 Đường Test, Hà Nội","phone":"0911222333","bloodType":"ab+",
                 "patientCode":"HACKED","status":"INACTIVE","userId":999,"id":999}
                """;

        mvc.perform(put("/api/v1/patients/me").header("Authorization", patient2Token())
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("Số 1 Đường Test, Hà Nội"))
                .andExpect(jsonPath("$.phone").value("0911222333"))
                .andExpect(jsonPath("$.bloodType").value("AB+"))
                .andExpect(jsonPath("$.patientCode").value("BN000002"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.userId").value((int) PATIENT2_USER_ID));

        Patient saved = patientRepository.findByUserId(PATIENT2_USER_ID).orElseThrow();
        assertEquals("BN000002", saved.getPatientCode());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals(2L, saved.getId());
    }

    @Test
    @DisplayName("PUT /patients/me của bệnh nhân A không đụng tới hồ sơ của B")
    void updateDoesNotAffectOtherPatient() throws Exception {
        String before = patientRepository.findByUserId(PATIENT2_USER_ID).orElseThrow().getAddress();

        mvc.perform(put("/api/v1/patients/me").header("Authorization", patientToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"address\":\"Địa chỉ của bệnh nhân A\"}"))
                .andExpect(status().isOk());

        assertEquals(before, patientRepository.findByUserId(PATIENT2_USER_ID).orElseThrow().getAddress());
        assertEquals("Địa chỉ của bệnh nhân A",
                patientRepository.findByUserId(PATIENT1_USER_ID).orElseThrow().getAddress());
    }

    @Test
    @DisplayName("PUT /patients/me với dữ liệu sai → 400 VALIDATION_FAILED kèm lỗi theo từng trường")
    void updateValidatesInput() throws Exception {
        String payload = """
                {"fullName":"   ","dateOfBirth":"2999-01-01","genderCode":"ROBOT","phone":"abc",
                 "email":"khong-phai-email","bloodType":"Z9","emergencyContactPhone":"1"}
                """;

        MvcResult result = mvc.perform(put("/api/v1/patients/me").header("Authorization", patientToken())
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();

        JsonNode details = body(result).get("details");
        for (String field : new String[] { "fullName", "dateOfBirth", "genderCode", "phone", "email", "bloodType",
                "emergencyContactPhone" }) {
            assertTrue(details.has(field), "thiếu lỗi cho trường " + field + ": " + details);
        }
    }

    @Test
    @DisplayName("Bác sĩ/Admin không gọi được /patients/me (chỉ bệnh nhân)")
    void meIsPatientOnly() throws Exception {
        mvc.perform(get("/api/v1/patients/me").header("Authorization", doctorToken()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/patients/me").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    // ---------- Bác sĩ ----------

    @Test
    @DisplayName("Bác sĩ chỉ thấy bệnh nhân thuộc phạm vi phụ trách trong danh sách")
    void doctorListsOnlyOwnPatients() throws Exception {
        var docA = data.isolatedDoctor("list_a");
        var docB = data.isolatedDoctor("list_b");
        Patient patA = data.extraPatient("ISO-LIST-A");
        Patient patB = data.extraPatient("ISO-LIST-B");
        data.appointment(docA.doctorId(), patA.getId(), AppointmentStatus.BOOKED);
        data.appointment(docB.doctorId(), patB.getId(), AppointmentStatus.BOOKED);

        MvcResult listA = mvc.perform(get("/api/v1/patients").header("Authorization", docA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        String codesA = body(listA).get("content").findValuesAsText("patientCode").toString();
        assertTrue(codesA.contains("ISO-LIST-A"), codesA);
        assertFalse(codesA.contains("ISO-LIST-B"), codesA);

        MvcResult listB = mvc.perform(get("/api/v1/patients").header("Authorization", docB.token()))
                .andExpect(status().isOk()).andReturn();
        String codesB = body(listB).get("content").findValuesAsText("patientCode").toString();
        assertTrue(codesB.contains("ISO-LIST-B"), codesB);
        assertFalse(codesB.contains("ISO-LIST-A"), codesB);
    }

    @Test
    @DisplayName("Tìm kiếm theo tên/mã, phân trang và ký tự đại diện bị vô hiệu hóa")
    void doctorSearchPaginationAndWildcards() throws Exception {
        var doc = data.isolatedDoctor("search");
        Patient own = data.extraPatient("ISO-SEARCH-OWN");
        Patient other = data.extraPatient("ISO-SEARCH-OTHER");
        data.appointment(doc.doctorId(), own.getId(), AppointmentStatus.BOOKED);

        mvc.perform(get("/api/v1/patients?keyword=iso-search-own").header("Authorization", doc.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/patients?keyword=khongcoai").header("Authorization", doc.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        // bệnh nhân của bác sĩ khác tồn tại nhưng không được tìm thấy qua bác sĩ này
        mvc.perform(get("/api/v1/patients?keyword=" + other.getPatientCode()).header("Authorization", doc.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        // "%" là ký tự thường (đã escape), không phải ký tự đại diện: không khớp bệnh nhân nào
        mvc.perform(get("/api/v1/patients?keyword=%25").header("Authorization", doc.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/patients?size=1000&page=0").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("Bác sĩ xem chi tiết bệnh nhân phụ trách → 200 + audit PATIENT_VIEW; không phụ trách → 403 + ACCESS_DENIED")
    void doctorPatientDetailScope() throws Exception {
        var doc = data.isolatedDoctor("detail");
        Patient own = data.extraPatient("ISO-DETAIL-OWN");
        Patient other = data.extraPatient("ISO-DETAIL-OTHER");
        data.appointment(doc.doctorId(), own.getId(), AppointmentStatus.BOOKED);

        long viewsBefore = countAudits(AuditActions.ENTITY_PATIENTS, own.getId(),
                AuditActions.PATIENT_VIEW, doc.userId());
        long deniedBefore = countAudits(AuditActions.ENTITY_PATIENTS, other.getId(),
                AuditActions.ACCESS_DENIED, doc.userId());

        mvc.perform(get("/api/v1/patients/" + own.getId()).header("Authorization", doc.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCode").value("ISO-DETAIL-OWN"));
        mvc.perform(get("/api/v1/patients/" + other.getId()).header("Authorization", doc.token()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/patients/999999").header("Authorization", doc.token()))
                .andExpect(status().isNotFound());

        assertEquals(viewsBefore + 1, countAudits(AuditActions.ENTITY_PATIENTS, own.getId(),
                AuditActions.PATIENT_VIEW, doc.userId()));
        assertEquals(deniedBefore + 1, countAudits(AuditActions.ENTITY_PATIENTS, other.getId(),
                AuditActions.ACCESS_DENIED, doc.userId()));
    }

    @Test
    @DisplayName("Bệnh nhân không xem được danh sách hay chi tiết bệnh nhân khác (chặn ở route)")
    void patientCannotBrowsePatients() throws Exception {
        mvc.perform(get("/api/v1/patients").header("Authorization", patientToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/patients/" + ApiTestData.PATIENT2_ID).header("Authorization", patientToken()))
                .andExpect(status().isForbidden());
    }

    // ---------- Admin ----------

    @Test
    @DisplayName("Admin chỉ nhận trường hành chính: không có dị ứng, nhóm máu, liên hệ khẩn cấp")
    void adminSeesAdministrativeFieldsOnly() throws Exception {
        MvcResult list = mvc.perform(get("/api/v1/patients").header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn();
        JsonNode first = body(list).get("content").get(0);
        assertTrue(first.has("patientCode") && first.has("fullName") && first.has("phone"));
        for (String forbidden : new String[] { "allergies", "bloodType", "emergencyContactName",
                "emergencyContactPhone" }) {
            assertFalse(first.has(forbidden), "Admin không được thấy " + forbidden);
        }

        MvcResult detail = mvc.perform(get("/api/v1/patients/" + ApiTestData.PATIENT1_ID)
                .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCode").value("BN000001"))
                .andReturn();
        assertFalse(body(detail).has("allergies"));
        assertFalse(body(detail).has("bloodType"));
    }

    // ---------- Đăng ký ----------

    @Test
    @DisplayName("Đăng ký tạo Patient (BN + 6 chữ số) và MedicalRecord (MR + 6 chữ số) trong cùng giao dịch")
    void registrationCreatesPatientAndMedicalRecord() throws Exception {
        String payload = """
                {"username":"be02_newpatient","password":"Sup3r#Secret","fullName":"Lê Thị Mới",
                 "email":"be02.new@example.com","phone":"0988777666","genderCode":"female"}
                """;

        MvcResult registered = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        long patientId = body(registered).get("patientId").asLong();
        String patientCode = body(registered).get("patientCode").asText();

        assertEquals("BN%06d".formatted(patientId), patientCode);
        Patient patient = patientRepository.findById(patientId).orElseThrow();
        assertEquals("FEMALE", patient.getGenderCode());
        var record = medicalRecordRepository.findByPatientId(patientId).orElseThrow();
        assertEquals("MR%06d".formatted(patientId), record.getRecordCode());
        assertEquals("ACTIVE", record.getStatus());

        // người mới đăng nhập được và dùng được API bệnh nhân bằng JWT thật
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"be02_newpatient\",\"password\":\"Sup3r#Secret\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = body(login).get("token").asText();
        mvc.perform(get("/api/v1/patients/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCode").value(patientCode));
        mvc.perform(get("/api/v1/medical-records/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCode").value(record.getRecordCode()));
    }

    @Test
    @DisplayName("Đăng ký với thông tin hồ sơ không hợp lệ → 400 theo từng trường, không tạo tài khoản")
    void registrationValidatesProfileFields() throws Exception {
        String payload = """
                {"username":"be02_invalid","password":"Sup3r#Secret","fullName":"Sai Dữ Liệu",
                 "email":"be02.invalid@example.com","phone":"abc","bloodType":"QUA-DAI-KHONG-HOP-LE",
                 "genderCode":"ROBOT","dateOfBirth":"2999-01-01"}
                """;

        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn();

        JsonNode details = body(result).get("details");
        for (String field : new String[] { "phone", "bloodType", "genderCode", "dateOfBirth" }) {
            assertTrue(details.has(field), "thiếu lỗi cho trường " + field + ": " + details);
        }
        assertFalse(userRepository.existsByUsername("be02_invalid"));
    }
}
