package com.phenikaa.cse702051.medbook.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalRecordRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * AC-04.1, 04.2, 04.6: Admin tạo tài khoản cho cả ba vai trò. Tài khoản, vai trò và hồ sơ đi kèm được tạo trong một
 * giao dịch: lỗi ở bước tạo hồ sơ thì không còn lại tài khoản nào.
 */
class AdminUserCreateTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "/api/v1/admin/users";
    private static final String PASSWORD = "Tao#Moi2026";

    @Autowired
    private UserRepository users;
    @Autowired
    private PatientRepository patients;
    @Autowired
    private DoctorRepository doctors;
    @Autowired
    private MedicalRecordRepository medicalRecords;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    private static String body(String username, String email, String role, String doctorProfile) {
        return "{\"username\":\"%s\",\"password\":\"%s\",\"fullName\":\"Người dùng %s\",\"email\":\"%s\","
                .formatted(username, PASSWORD, username, email)
                + "\"phone\":\"0987 654 321\",\"role\":\"%s\"%s}".formatted(role,
                        doctorProfile == null ? "" : ",\"doctorProfile\":" + doctorProfile);
    }

    private static String doctorProfile(Object specialtyId, String license) {
        return "{\"specialtyId\":%s,\"licenseNumber\":\"%s\",\"bio\":\"Bác sĩ mới\"}".formatted(specialtyId, license);
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post(URL).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode login(String username) throws Exception {
        return json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk()));
    }

    // ---------- tạo theo vai trò ----------

    @Test
    @DisplayName("AC-04.1 Tạo bệnh nhân: 201, có hồ sơ bệnh nhân và bệnh án, đăng nhập được, không lộ băm mật khẩu")
    void createsPatientWithProfileAndRecord() throws Exception {
        String username = "nb_" + unique();
        ResultActions result = create(body(username, username.toUpperCase() + "@Example.com", "patient", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("PATIENT"))
                .andExpect(jsonPath("$.patientId").isNumber())
                .andExpect(jsonPath("$.doctorId").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokenVersion").doesNotExist());
        JsonNode created = json(result);
        long userId = created.get("id").asLong();

        Patient patient = patients.findById(created.get("patientId").asLong()).orElseThrow();
        assertEquals("Người dùng " + username, patient.getFullName());
        assertEquals("0987 654 321", patient.getPhone());
        assertTrue(patient.getPatientCode().startsWith("BN"));
        assertTrue(medicalRecords.findByPatientId(patient.getId()).isPresent(), "bệnh nhân mới phải có bệnh án");

        JsonNode session = login(username);
        assertEquals("PATIENT", session.get("roles").get(0).asText());
        mvc.perform(get("/api/v1/patients/me").header("Authorization", "Bearer " + session.get("token").asText()))
                .andExpect(status().isOk());

        assertEquals(1, countAudits(AuditActions.ENTITY_USERS, userId, AuditActions.USER_CREATE, ADMIN_USER_ID));
        assertFalse(auditsOf(AuditActions.ENTITY_USERS, userId).stream()
                .anyMatch(a -> a.getMetadataJson() != null && a.getMetadataJson().contains(PASSWORD)));
    }

    @Test
    @DisplayName("AC-04.1 Tạo bác sĩ kèm hồ sơ: 201, hồ sơ bác sĩ hiện ở danh sách công khai, đăng nhập vào được lịch làm việc")
    void createsDoctorWithProfile() throws Exception {
        String username = "bs_" + unique();
        String license = "CCHN-" + unique().toUpperCase();
        JsonNode created = json(create(body(username, username + "@example.com", "DOCTOR", doctorProfile(2, license)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("DOCTOR"))
                .andExpect(jsonPath("$.doctorId").isNumber())
                .andExpect(jsonPath("$.patientId").doesNotExist()));

        Doctor doctor = doctors.findById(created.get("doctorId").asLong()).orElseThrow();
        assertEquals(created.get("id").asLong(), doctor.getUserId());
        assertEquals("Người dùng " + username, doctor.getFullName());
        assertEquals(license, doctor.getLicenseNumber());
        assertEquals("0987 654 321", doctor.getPhone(), "điện thoại hồ sơ lấy từ tài khoản khi không nhập riêng");
        assertTrue(Boolean.TRUE.equals(doctor.getIsActive()));

        mvc.perform(get("/api/v1/doctors/" + doctor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialtyName").value("Nhi khoa"));

        String token = "Bearer " + login(username).get("token").asText();
        mvc.perform(get("/api/v1/doctor-schedules").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-04.1 Tạo quản trị viên: 201, không có hồ sơ bệnh nhân hay bác sĩ, dùng được trang quản trị")
    void createsAdminWithoutProfiles() throws Exception {
        String username = "qt_" + unique();
        JsonNode created = json(create(body(username, username + "@example.com", "ADMIN", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.patientId").doesNotExist())
                .andExpect(jsonPath("$.doctorId").doesNotExist()));
        long userId = created.get("id").asLong();
        assertTrue(patients.findByUserId(userId).isEmpty());
        assertTrue(doctors.findByUserId(userId).isEmpty());

        String token = "Bearer " + login(username).get("token").asText();
        mvc.perform(get(URL + "/" + userId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    // ---------- một giao dịch ----------

    @Test
    @DisplayName("AC-04.2 Chuyên khoa không tồn tại: 400 và KHÔNG còn lại tài khoản (hoàn tác cả giao dịch)")
    void doctorWithUnknownSpecialtyLeavesNoAccount() throws Exception {
        String username = "bs_" + unique();
        long before = users.count();

        create(body(username, username + "@example.com", "DOCTOR", doctorProfile(999_999, "CCHN-" + unique())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.specialtyId").isNotEmpty());

        assertFalse(users.existsByUsername(username), "tài khoản phải bị hoàn tác cùng hồ sơ bác sĩ");
        assertEquals(before, users.count());
    }

    @Test
    @DisplayName("AC-04.2 Trùng số giấy phép hành nghề: 409 và KHÔNG còn lại tài khoản; tạo lại với số khác thì được")
    void doctorWithDuplicateLicenseLeavesNoAccount() throws Exception {
        String license = "CCHN-" + unique().toUpperCase();
        String first = "bs_" + unique();
        create(body(first, first + "@example.com", "DOCTOR", doctorProfile(1, license)))
                .andExpect(status().isCreated());

        String second = "bs_" + unique();
        create(body(second, second + "@example.com", "DOCTOR", doctorProfile(1, license.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.licenseNumber").isNotEmpty());
        assertFalse(users.existsByUsername(second));

        // tên đăng nhập và email không bị "giữ chỗ" bởi lần thất bại
        JsonNode retried = json(create(body(second, second + "@example.com", "DOCTOR",
                doctorProfile(1, "CCHN-" + unique().toUpperCase()))).andExpect(status().isCreated()));
        assertNotNull(retried.get("doctorId"));
    }

    // ---------- trùng và sai dữ liệu ----------

    @Test
    @DisplayName("AC-04.6 Trùng tên đăng nhập hoặc email (không phân biệt hoa thường): 409 kèm tên trường")
    void duplicateUsernameOrEmailIsConflict() throws Exception {
        String username = "nb_" + unique();
        create(body(username, username + "@example.com", "PATIENT", null)).andExpect(status().isCreated());

        create(body(username.toUpperCase(), "khac_" + username + "@example.com", "PATIENT", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.username").isNotEmpty());
        create(body("khac_" + username, username.toUpperCase() + "@EXAMPLE.COM", "PATIENT", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.email").isNotEmpty());
        assertFalse(users.existsByUsername("khac_" + username));
    }

    @Test
    @DisplayName("AC-04.6 Vai trò lạ, bác sĩ thiếu hồ sơ, vai trò khác kèm hồ sơ bác sĩ: 400 kèm tên trường")
    void invalidRoleAndProfileCombinationsAreRejected() throws Exception {
        String username = "sai_" + unique();
        create(body(username, username + "@example.com", "NURSE", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.role").isNotEmpty());
        create(body(username, username + "@example.com", "DOCTOR", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.doctorProfile").isNotEmpty());
        create(body(username, username + "@example.com", "PATIENT", doctorProfile(1, "CCHN-" + unique())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.doctorProfile").isNotEmpty());
        create(body(username, username + "@example.com", "DOCTOR", "{\"specialtyId\":null,\"licenseNumber\":\" \"}"))
                .andExpect(status().isBadRequest());
        assertFalse(users.existsByUsername(username));
    }

    @Test
    @DisplayName("AC-04.6 Mật khẩu yếu, email sai định dạng, tên đăng nhập có ký tự lạ, thiếu trường: 400 kèm tên trường")
    void invalidFieldsAreRejected() throws Exception {
        String username = "sai_" + unique();
        create("{\"username\":\"%s\",\"password\":\"matkhauyeu\",\"fullName\":\"A\",\"email\":\"%s@example.com\","
                .formatted(username, username) + "\"role\":\"PATIENT\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").isNotEmpty());
        create(body(username, "khong-phai-email", "PATIENT", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").isNotEmpty());
        create(body("ten co dau cach", username + "@example.com", "PATIENT", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.username").isNotEmpty());
        create("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.username").isNotEmpty())
                .andExpect(jsonPath("$.details.password").isNotEmpty())
                .andExpect(jsonPath("$.details.fullName").isNotEmpty())
                .andExpect(jsonPath("$.details.email").isNotEmpty())
                .andExpect(jsonPath("$.details.role").isNotEmpty());
        assertFalse(users.existsByUsername(username));
    }

    // ---------- phân quyền ----------

    @Test
    @DisplayName("AC-04.5 Bệnh nhân và bác sĩ không dùng được API quản trị tài khoản (403); chưa đăng nhập 401")
    void nonAdminsCannotManageUsers() throws Exception {
        String username = "cam_" + unique();
        for (String token : new String[] { patientToken(), doctorToken() }) {
            mvc.perform(get(URL).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get(URL + "/" + ADMIN_USER_ID).header("Authorization", token))
                    .andExpect(status().isForbidden());
            mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content(body(username, username + "@example.com", "ADMIN", null)))
                    .andExpect(status().isForbidden());
            mvc.perform(put(URL + "/" + PATIENT2_USER_ID).header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fullName\":\"X\",\"email\":\"x@example.com\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(patch(URL + "/" + PATIENT2_USER_ID + "/status").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put(URL + "/" + PATIENT1_USER_ID + "/roles").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(body(username, username + "@example.com", "ADMIN", null)))
                .andExpect(status().isUnauthorized());

        assertFalse(users.existsByUsername(username));
        assertEquals("ACTIVE", users.findById(PATIENT2_USER_ID).orElseThrow().getStatus());
    }
}
