package com.phenikaa.cse702051.medbook.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-04.7: {@code /users/me} cho cả ba vai trò. Người dùng luôn lấy từ JWT; chỉ sửa được họ tên, điện thoại, email
 * và thay đổi được đồng bộ sang hồ sơ bệnh nhân/bác sĩ mà không làm mất phiên đăng nhập.
 */
class UserProfileApiTest extends AbstractApiTest {

    private static final String ME = "/api/v1/users/me";

    @Autowired
    private UserRepository users;
    @Autowired
    private PatientRepository patients;
    @Autowired
    private DoctorRepository doctors;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    private ResultActions update(String token, String body) throws Exception {
        return mvc.perform(put(ME).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("AC-04.7 GET /users/me: mỗi vai trò thấy đúng tài khoản của mình kèm patientId/doctorId, không lộ băm mật khẩu")
    void eachRoleSeesOwnAccount() throws Exception {
        mvc.perform(get(ME).header("Authorization", patientToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PATIENT1_USER_ID))
                .andExpect(jsonPath("$.username").value("patient1"))
                .andExpect(jsonPath("$.roles[0]").value("PATIENT"))
                .andExpect(jsonPath("$.patientId").value(ApiTestData.PATIENT1_ID))
                .andExpect(jsonPath("$.doctorId").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokenVersion").doesNotExist());
        mvc.perform(get(ME).header("Authorization", doctor2Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DOCTOR2_USER_ID))
                .andExpect(jsonPath("$.roles[0]").value("DOCTOR"))
                .andExpect(jsonPath("$.doctorId").value(ApiTestData.DOCTOR2_ID))
                .andExpect(jsonPath("$.patientId").doesNotExist());
        mvc.perform(get(ME).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ADMIN_USER_ID))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.patientId").doesNotExist())
                .andExpect(jsonPath("$.doctorId").doesNotExist());

        mvc.perform(get(ME)).andExpect(status().isUnauthorized());
        mvc.perform(put(ME).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"X\",\"email\":\"x@example.com\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-04.7 Bệnh nhân sửa hồ sơ tài khoản: đồng bộ sang hồ sơ bệnh nhân, phiên vẫn dùng được, ghi audit")
    void patientUpdatesOwnContactAndProfileIsSynced() throws Exception {
        String key = unique();
        IsolatedPatient patient = data.isolatedPatient("me" + key);
        int versionBefore = users.findById(patient.userId()).orElseThrow().getTokenVersion();

        update(patient.token(),
                "{\"fullName\":\" Phạm Văn Mới \",\"phone\":\"+84 912 345 678\",\"email\":\"Moi_%s@Example.com\"}"
                        .formatted(key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Phạm Văn Mới"))
                .andExpect(jsonPath("$.phone").value("+84 912 345 678"))
                .andExpect(jsonPath("$.email").value("moi_" + key + "@example.com"))
                .andExpect(jsonPath("$.patientId").value(patient.patientId()));

        Patient profile = patients.findById(patient.patientId()).orElseThrow();
        assertEquals("Phạm Văn Mới", profile.getFullName());
        assertEquals("+84 912 345 678", profile.getPhone());
        assertEquals("moi_" + key + "@example.com", profile.getEmail());

        assertEquals(versionBefore, users.findById(patient.userId()).orElseThrow().getTokenVersion());
        mvc.perform(get("/api/v1/patients/me").header("Authorization", patient.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Phạm Văn Mới"));

        assertEquals(1, countAudits(AuditActions.ENTITY_USERS, patient.userId(), AuditActions.USER_UPDATE,
                patient.userId()));
        String metadata = auditsOf(AuditActions.ENTITY_USERS, patient.userId()).getLast().getMetadataJson()
                .replace("\\", "").replace(" ", "");
        assertTrue(metadata.contains("fullName") && metadata.contains("phone") && metadata.contains("email"));
    }

    @Test
    @DisplayName("AC-04.7 Bác sĩ sửa hồ sơ tài khoản: họ tên và điện thoại hiện ở hồ sơ bác sĩ công khai")
    void doctorUpdateIsSyncedToDoctorProfile() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("me" + key);

        update(doctor.token(), "{\"fullName\":\"BS. Trần Đổi Tên\",\"phone\":\"0933 444 555\","
                + "\"email\":\"isodoc_me%s@example.com\"}".formatted(key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(doctor.doctorId()));

        Doctor profile = doctors.findById(doctor.doctorId()).orElseThrow();
        assertEquals("BS. Trần Đổi Tên", profile.getFullName());
        assertEquals("0933 444 555", profile.getPhone());
        mvc.perform(get("/api/v1/doctors/" + doctor.doctorId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("BS. Trần Đổi Tên"));
    }

    @Test
    @DisplayName("AC-04.7 Không đổi được tên đăng nhập, vai trò, trạng thái, mật khẩu qua /users/me (các trường đó bị bỏ qua)")
    void protectedFieldsAreIgnored() throws Exception {
        String key = unique();
        IsolatedPatient patient = data.isolatedPatient("me" + key);
        User before = users.findById(patient.userId()).orElseThrow();

        update(patient.token(), """
                {"fullName":"Tên Hợp Lệ","email":"%s","phone":"0900000000","id":1,"username":"admin_gia",
                 "roles":["ADMIN"],"role":"ADMIN","status":"LOCKED","tokenVersion":99,"passwordHash":"x",
                 "password":"Khac#12345","patientId":1,"doctorId":1}
                """.formatted(before.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.userId()))
                .andExpect(jsonPath("$.username").value(before.getUsername()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("PATIENT"))
                .andExpect(jsonPath("$.patientId").value(patient.patientId()));

        User after = users.findById(patient.userId()).orElseThrow();
        assertEquals(before.getPasswordHash(), after.getPasswordHash());
        assertEquals(before.getTokenVersion(), after.getTokenVersion());
        assertEquals("Tên Hợp Lệ", after.getFullName());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", patient.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-04.6 Email trùng tài khoản khác (không phân biệt hoa thường) 409; đổi kiểu chữ email của chính mình thì được")
    void duplicateEmailIsConflictButOwnEmailIsFine() throws Exception {
        String key = unique();
        IsolatedPatient patient = data.isolatedPatient("me" + key);
        User before = users.findById(patient.userId()).orElseThrow();

        update(patient.token(), "{\"fullName\":\"Tên Khác\",\"email\":\"Patient2@MedBook.local\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.email").isNotEmpty());
        User unchanged = users.findById(patient.userId()).orElseThrow();
        assertEquals(before.getEmail(), unchanged.getEmail());
        assertEquals(before.getFullName(), unchanged.getFullName(), "lỗi trùng email không được lưu một phần");

        update(patient.token(), "{\"fullName\":\"%s\",\"phone\":\"%s\",\"email\":\"%s\"}"
                .formatted(before.getFullName(), before.getPhone(), before.getEmail().toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(before.getEmail()));
        assertEquals(0, countAudits(AuditActions.ENTITY_USERS, patient.userId(), AuditActions.USER_UPDATE,
                patient.userId()), "không có gì thay đổi thì không ghi audit");
    }

    @Test
    @DisplayName("AC-04.6 Họ tên rỗng, email sai, điện thoại sai, quá dài: 400 kèm tên trường; bỏ trống điện thoại thì xóa số")
    void validatesFieldsAndAllowsClearingPhone() throws Exception {
        String key = unique();
        IsolatedPatient patient = data.isolatedPatient("me" + key);
        String email = users.findById(patient.userId()).orElseThrow().getEmail();

        update(patient.token(), "{\"fullName\":\"  \",\"email\":\"khong-phai-email\",\"phone\":\"12ab\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fullName").isNotEmpty())
                .andExpect(jsonPath("$.details.email").isNotEmpty())
                .andExpect(jsonPath("$.details.phone").isNotEmpty());
        update(patient.token(), "{\"fullName\":\"%s\",\"email\":\"%s\"}".formatted("A".repeat(151), email))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fullName").isNotEmpty());
        update(patient.token(), "{}").andExpect(status().isBadRequest());

        update(patient.token(), "{\"fullName\":\"Tên Giữ Nguyên\",\"email\":\"%s\",\"phone\":\"\"}".formatted(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());
        assertEquals(null, users.findById(patient.userId()).orElseThrow().getPhone());
        // cột điện thoại của hồ sơ bệnh nhân bắt buộc có giá trị nên giữ số cũ
        assertEquals("0900000002", patients.findById(patient.patientId()).orElseThrow().getPhone());
    }
}
