package com.phenikaa.cse702051.medbook.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-04.3, 04.4, 04.5: Admin xem danh sách, sửa, khóa/mở và đổi vai trò tài khoản. Khóa và đổi vai trò làm token cũ
 * hết hiệu lực ngay; danh sách không bao giờ lộ băm mật khẩu.
 */
class AdminUserManageTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "/api/v1/admin/users";
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

    private ResultActions asAdmin(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", adminToken());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String tokenOf(Long userId) {
        return bearer(loginSessions.create(userId).token());
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    // ---------- khóa và mở ----------

    @Test
    @DisplayName("AC-04.3 Khóa tài khoản: trạng thái LOCKED, token đang dùng bị từ chối ngay, ghi audit kèm lý do")
    void lockingInvalidatesExistingTokens() throws Exception {
        IsolatedPatient patient = data.isolatedPatient("lock" + unique());
        mvc.perform(get(ME).header("Authorization", patient.token())).andExpect(status().isOk());

        asAdmin(patch(URL + "/" + patient.userId() + "/status"),
                "{\"status\":\"locked\",\"reason\":\"Vi phạm quy định\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.userId()))
                .andExpect(jsonPath("$.status").value("LOCKED"))
                .andExpect(jsonPath("$.patientId").value(patient.patientId()));

        mvc.perform(get(ME).header("Authorization", patient.token())).andExpect(status().isUnauthorized());
        assertEquals(1, countAudits(AuditActions.ENTITY_USERS, patient.userId(),
                AuditActions.ACCOUNT_STATUS_CHANGED, ADMIN_USER_ID));
        String metadata = auditsOf(AuditActions.ENTITY_USERS, patient.userId()).getLast().getMetadataJson();
        assertTrue(metadata.contains("Vi phạm quy định"));
    }

    @Test
    @DisplayName("AC-04.3 Mở khóa: ACTIVE trở lại, xóa bộ đếm đăng nhập sai, phiên mới dùng được; token trước khi khóa vẫn vô hiệu")
    void unlockingRestoresAccess() throws Exception {
        IsolatedPatient patient = data.isolatedPatient("unlock" + unique());
        String oldToken = patient.token();
        asAdmin(patch(URL + "/" + patient.userId() + "/status"), "{\"status\":\"LOCKED\"}")
                .andExpect(status().isOk());
        User locked = users.findById(patient.userId()).orElseThrow();
        locked.setFailedLoginCount(4);
        users.save(locked);

        asAdmin(patch(URL + "/" + patient.userId() + "/status"), "{\"status\":\"ACTIVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.failedLoginCount").value(0))
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());

        mvc.perform(get(ME).header("Authorization", tokenOf(patient.userId()))).andExpect(status().isOk());
        mvc.perform(get(ME).header("Authorization", oldToken)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-04.3 Tự khóa mình 409; trạng thái lạ hoặc thiếu 400; tài khoản không tồn tại 404")
    void statusChangeRejectsInvalidRequests() throws Exception {
        asAdmin(patch(URL + "/" + ADMIN_USER_ID + "/status"), "{\"status\":\"LOCKED\"}")
                .andExpect(status().isConflict());
        assertEquals("ACTIVE", users.findById(ADMIN_USER_ID).orElseThrow().getStatus());

        asAdmin(patch(URL + "/" + PATIENT2_USER_ID + "/status"), "{\"status\":\"INACTIVE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").isNotEmpty());
        asAdmin(patch(URL + "/" + PATIENT2_USER_ID + "/status"), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").isNotEmpty());
        assertEquals("ACTIVE", users.findById(PATIENT2_USER_ID).orElseThrow().getStatus());

        asAdmin(patch(URL + "/999999/status"), "{\"status\":\"LOCKED\"}").andExpect(status().isNotFound());
    }

    // ---------- vai trò ----------

    @Test
    @DisplayName("AC-04.4 Đổi vai trò: thay toàn bộ, token mang vai trò cũ bị từ chối, phiên mới có quyền mới, ghi audit")
    void changingRolesReplacesThemAndInvalidatesTokens() throws Exception {
        User account = data.user("role_" + unique(), "Iso#Pass123", "PATIENT");
        String oldToken = tokenOf(account.getId());
        mvc.perform(get(URL).header("Authorization", oldToken)).andExpect(status().isForbidden());

        JsonNode changed = json(asAdmin(put(URL + "/" + account.getId() + "/roles"),
                "{\"roles\":[\"admin\",\"DOCTOR\",\"ADMIN\"]}").andExpect(status().isOk()));
        List<String> roles = texts(changed.get("roles"));
        assertEquals(2, roles.size());
        assertTrue(roles.containsAll(List.of("ADMIN", "DOCTOR")));

        mvc.perform(get(ME).header("Authorization", oldToken)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).header("Authorization", tokenOf(account.getId()))).andExpect(status().isOk());
        assertEquals(1, countAudits(AuditActions.ENTITY_USERS, account.getId(), AuditActions.ROLE_CHANGED,
                ADMIN_USER_ID));
    }

    @Test
    @DisplayName("AC-04.4 Danh sách vai trò rỗng hoặc có mã lạ 400; tự gỡ quyền ADMIN của mình 409; không tồn tại 404")
    void roleChangeRejectsInvalidRequests() throws Exception {
        User account = data.user("role_" + unique(), "Iso#Pass123", "PATIENT");
        for (String body : new String[] { "{\"roles\":[]}", "{}", "{\"roles\":[\"NURSE\"]}" }) {
            asAdmin(put(URL + "/" + account.getId() + "/roles"), body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.roles").isNotEmpty());
        }
        asAdmin(put(URL + "/" + ADMIN_USER_ID + "/roles"), "{\"roles\":[\"PATIENT\"]}")
                .andExpect(status().isConflict());
        asAdmin(put(URL + "/999999/roles"), "{\"roles\":[\"PATIENT\"]}").andExpect(status().isNotFound());

        JsonNode unchanged = json(asAdmin(get(URL + "/" + account.getId()), null).andExpect(status().isOk()));
        assertEquals(List.of("PATIENT"), texts(unchanged.get("roles")));
        asAdmin(get(URL), null).andExpect(status().isOk()); // quản trị viên vẫn còn quyền
    }

    @Test
    @DisplayName("AC-04.4 Thêm vai trò bác sĩ không tự tạo hồ sơ bác sĩ: doctorId vẫn rỗng cho tới khi Admin tạo hồ sơ")
    void addingDoctorRoleDoesNotCreateProfile() throws Exception {
        User account = data.user("role_" + unique(), "Iso#Pass123", "PATIENT");

        asAdmin(put(URL + "/" + account.getId() + "/roles"), "{\"roles\":[\"DOCTOR\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("DOCTOR"))
                .andExpect(jsonPath("$.doctorId").doesNotExist());
        assertTrue(doctors.findByUserId(account.getId()).isEmpty());
    }

    // ---------- danh sách ----------

    @Test
    @DisplayName("AC-04.5 Danh sách: lọc theo từ khóa (tên đăng nhập, email, họ tên), vai trò, trạng thái; ký tự % không là đại diện")
    void listFiltersByKeywordRoleAndStatus() throws Exception {
        String key = unique();
        IsolatedPatient patient = data.isolatedPatient("lst" + key);
        IsolatedDoctor doctor = data.isolatedDoctor("lst" + key);
        asAdmin(patch(URL + "/" + doctor.userId() + "/status"), "{\"status\":\"LOCKED\"}").andExpect(status().isOk());

        JsonNode both = json(asAdmin(get(URL).param("keyword", "LST" + key.toUpperCase()), null)
                .andExpect(status().isOk()));
        assertEquals(2, both.get("totalElements").asInt());

        JsonNode onlyPatients = json(asAdmin(get(URL).param("keyword", "lst" + key).param("role", "patient"), null)
                .andExpect(status().isOk()));
        assertEquals(1, onlyPatients.get("totalElements").asInt());
        JsonNode row = onlyPatients.get("content").get(0);
        assertEquals(patient.userId(), row.get("id").asLong());
        assertEquals(patient.patientId(), row.get("patientId").asLong());
        assertEquals(List.of("PATIENT"), texts(row.get("roles")));

        JsonNode onlyLocked = json(asAdmin(get(URL).param("keyword", "lst" + key).param("status", "LOCKED"), null)
                .andExpect(status().isOk()));
        assertEquals(1, onlyLocked.get("totalElements").asInt());
        assertEquals(doctor.userId(), onlyLocked.get("content").get(0).get("id").asLong());
        assertEquals(doctor.doctorId(), onlyLocked.get("content").get(0).get("doctorId").asLong());

        // tìm theo email và theo họ tên
        assertEquals(1, json(asAdmin(get(URL).param("keyword", "isopat_lst" + key + "@example.com"), null))
                .get("totalElements").asInt());
        assertEquals(1, json(asAdmin(get(URL).param("keyword", "Test isopat_lst" + key), null))
                .get("totalElements").asInt());
        assertEquals(0, json(asAdmin(get(URL).param("keyword", "%" + key + "%_"), null))
                .get("totalElements").asInt());

        asAdmin(get(URL).param("role", "NURSE"), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.role").isNotEmpty());
        asAdmin(get(URL).param("status", "INACTIVE"), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.status").isNotEmpty());
    }

    @Test
    @DisplayName("AC-04.5 Danh sách: phân trang (size tối đa 100), sắp xếp theo danh sách trắng, không lộ trường nhạy cảm")
    void listPaginatesSortsAndHidesSecrets() throws Exception {
        String key = unique();
        data.user("srt_" + key + "_b", "Iso#Pass123", "PATIENT");
        data.user("srt_" + key + "_a", "Iso#Pass123", "PATIENT");
        data.user("srt_" + key + "_c", "Iso#Pass123", "ADMIN");

        JsonNode ascending = json(asAdmin(get(URL).param("keyword", "srt_" + key).param("sort", "username"), null)
                .andExpect(status().isOk()));
        assertEquals(List.of("srt_" + key + "_a", "srt_" + key + "_b", "srt_" + key + "_c"),
                ascending.get("content").findValuesAsText("username"));

        JsonNode paged = json(asAdmin(get(URL).param("keyword", "srt_" + key).param("sort", "username,desc")
                .param("size", "2").param("page", "1"), null).andExpect(status().isOk()));
        assertEquals(3, paged.get("totalElements").asInt());
        assertEquals(2, paged.get("totalPages").asInt());
        assertEquals(1, paged.get("content").size());
        assertEquals("srt_" + key + "_a", paged.get("content").get(0).get("username").asText());

        ResultActions all = asAdmin(get(URL).param("size", "5000"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        String raw = all.andReturn().getResponse().getContentAsString();
        assertFalse(raw.contains("passwordHash"));
        assertFalse(raw.contains("tokenVersion"));
        assertFalse(raw.contains("$2a$"), "không được có băm BCrypt trong phản hồi");

        asAdmin(get(URL).param("sort", "passwordHash"), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.sort").isNotEmpty());
        asAdmin(get(URL + "/999999"), null).andExpect(status().isNotFound());
    }

    // ---------- sửa ----------

    @Test
    @DisplayName("AC-04.5 Admin sửa họ tên, điện thoại, email: đồng bộ sang hồ sơ bệnh nhân, không làm mất phiên của người đó")
    void adminUpdatesContactAndSyncsProfile() throws Exception {
        String key = unique();
        IsolatedPatient patient = data.isolatedPatient("upd" + key);

        asAdmin(put(URL + "/" + patient.userId()),
                "{\"fullName\":\"  Lê Thị Sửa  \",\"phone\":\"0911 222 333\",\"email\":\"Sua_%s@Example.com\"}"
                        .formatted(key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Lê Thị Sửa"))
                .andExpect(jsonPath("$.phone").value("0911 222 333"))
                .andExpect(jsonPath("$.email").value("sua_" + key + "@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("PATIENT"));

        var profile = patients.findById(patient.patientId()).orElseThrow();
        assertEquals("Lê Thị Sửa", profile.getFullName());
        assertEquals("0911 222 333", profile.getPhone());
        assertEquals("sua_" + key + "@example.com", profile.getEmail());
        mvc.perform(get(ME).header("Authorization", patient.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Lê Thị Sửa"));
        assertEquals(1, countAudits(AuditActions.ENTITY_USERS, patient.userId(), AuditActions.USER_UPDATE,
                ADMIN_USER_ID));
    }

    @Test
    @DisplayName("AC-04.6 Admin sửa email trùng tài khoản khác 409; dữ liệu sai 400; không tồn tại 404; không đổi gì khi lỗi")
    void adminUpdateRejectsDuplicatesAndInvalidData() throws Exception {
        IsolatedPatient patient = data.isolatedPatient("upd" + unique());
        String before = users.findById(patient.userId()).orElseThrow().getEmail();

        asAdmin(put(URL + "/" + patient.userId()), "{\"fullName\":\"Tên mới\",\"email\":\"ADMIN1@medbook.local\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.email").isNotEmpty());
        asAdmin(put(URL + "/" + patient.userId()), "{\"fullName\":\" \",\"email\":\"sai\",\"phone\":\"abc\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fullName").isNotEmpty())
                .andExpect(jsonPath("$.details.email").isNotEmpty())
                .andExpect(jsonPath("$.details.phone").isNotEmpty());
        asAdmin(put(URL + "/999999"), "{\"fullName\":\"Tên mới\",\"email\":\"moi@example.com\"}")
                .andExpect(status().isNotFound());

        User unchanged = users.findById(patient.userId()).orElseThrow();
        assertEquals(before, unchanged.getEmail());
        assertEquals("Test " + unchanged.getUsername(), unchanged.getFullName());
    }
}
