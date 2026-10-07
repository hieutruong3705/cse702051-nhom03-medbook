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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * Danh mục nhìn từ phía khách, bác sĩ và bệnh nhân: chỉ bản ghi đang hoạt động lọt ra ngoài, tìm kiếm không bị
 * ký tự đại diện đánh lừa, và các đường dẫn quản trị chỉ dành cho Admin.
 */
class CatalogPublicAndAccessTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private JsonNode adminCreate(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/" + path).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode page(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    // ---------- công khai ----------

    @Test
    @DisplayName("AC-01.1 Khách xem chuyên khoa: 200 dạng trang, chỉ có bản ACTIVE kể cả khi tìm bằng từ khóa")
    void guestSeesOnlyActiveSpecialties() throws Exception {
        String token = unique();
        long active = adminCreate("specialties",
                "{\"code\":\"SPA%s\",\"name\":\"Khoa mở %s\"}".formatted(token, token)).get("id").asLong();
        long inactive = adminCreate("specialties",
                "{\"code\":\"SPI%s\",\"name\":\"Khoa đóng %s\",\"status\":\"INACTIVE\"}".formatted(token, token))
                .get("id").asLong();

        JsonNode found = page(mvc.perform(get("/api/v1/specialties").param("keyword", token.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].code").value("SPA" + token))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
                .andReturn());
        assertEquals(List.of(active), ids(found));

        // tra theo id: bản đang hoạt động thấy được, bản đã ngừng sử dụng coi như không tồn tại
        mvc.perform(get("/api/v1/specialties/" + active)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Khoa mở " + token));
        mvc.perform(get("/api/v1/specialties/" + inactive)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/specialties/999999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-01.1, AC-01.9 Khách xem dịch vụ: trang gồm đủ trường mà màn hình đặt lịch dùng, ẩn dịch vụ đã ngừng")
    void guestSeesOnlyActiveServicesWithBookingFields() throws Exception {
        String token = unique();
        long active = adminCreate("medical-services", """
                {"code":"SVA%s","name":"Dịch vụ mở %s","description":"Mô tả","durationMinutes":25,"price":123456.5}
                """.formatted(token, token)).get("id").asLong();
        long inactive = adminCreate("medical-services", """
                {"code":"SVI%s","name":"Dịch vụ đóng %s","durationMinutes":25,"price":1,"status":"INACTIVE"}
                """.formatted(token, token)).get("id").asLong();

        mvc.perform(get("/api/v1/medical-services").param("keyword", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active))
                .andExpect(jsonPath("$.content[0].code").value("SVA" + token))
                .andExpect(jsonPath("$.content[0].name").value("Dịch vụ mở " + token))
                .andExpect(jsonPath("$.content[0].description").value("Mô tả"))
                .andExpect(jsonPath("$.content[0].durationMinutes").value(25))
                .andExpect(jsonPath("$.content[0].price").value(123456.5));
        mvc.perform(get("/api/v1/medical-services/" + active)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/medical-services/" + inactive)).andExpect(status().isNotFound());

        // dịch vụ seed mà luồng đặt lịch và hóa đơn đang dùng vẫn có trong danh sách
        JsonNode all = page(mvc.perform(get("/api/v1/medical-services").param("size", "100"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(ids(all).contains(1L), "dịch vụ seed DV01 phải có trong danh sách công khai");
        assertFalse(ids(all).contains(inactive));
    }

    @Test
    @DisplayName("AC-01.10 Từ khóa % và _ là ký tự thường: không khớp mọi bản ghi")
    void wildcardCharactersAreLiteral() throws Exception {
        String token = unique();
        adminCreate("specialties", "{\"code\":\"WPC%s\",\"name\":\"Giảm 50%% phí %s\"}".formatted(token, token));
        adminCreate("specialties", "{\"code\":\"WUS%s\",\"name\":\"Khoa a_b %s\"}".formatted(token, token));
        adminCreate("specialties", "{\"code\":\"WPL%s\",\"name\":\"Khoa thường %s\"}".formatted(token, token));

        long total = page(mvc.perform(get("/api/v1/specialties").param("size", "1"))
                .andExpect(status().isOk()).andReturn()).get("totalElements").asLong();

        for (String wildcard : new String[] { "%", "_" }) {
            JsonNode found = page(mvc.perform(get("/api/v1/specialties").param("keyword", wildcard)
                    .param("size", "100")).andExpect(status().isOk()).andReturn());
            assertTrue(found.get("totalElements").asLong() >= 1, "phải tìm thấy bản ghi chứa ký tự " + wildcard);
            assertTrue(found.get("totalElements").asLong() < total,
                    "từ khóa " + wildcard + " không được khớp mọi bản ghi");
            found.get("content").forEach(node -> assertTrue(
                    node.get("name").asText().contains(wildcard) || node.get("code").asText().contains(wildcard),
                    "kết quả phải chứa đúng ký tự " + wildcard + ": " + node));
        }
    }

    @Test
    @DisplayName("Phân trang: size lớn hơn 100 bị cắt còn 100, page âm coi như 0")
    void pageSizeIsCapped() throws Exception {
        mvc.perform(get("/api/v1/specialties").param("size", "5000").param("page", "-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.page").value(0));
        mvc.perform(get("/api/v1/medical-services").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
    }

    // ---------- quyền theo vai trò ----------

    @Test
    @DisplayName("AC-01.2 Thuốc: khách 401, bệnh nhân 403, bác sĩ chỉ thấy ACTIVE, Admin lọc được INACTIVE")
    void medicinesVisibilityByRole() throws Exception {
        String token = unique();
        long active = adminCreate("medicines",
                "{\"code\":\"MDA%s\",\"name\":\"Thuốc mở %s\",\"unit\":\"Viên\"}".formatted(token, token))
                .get("id").asLong();
        long inactive = adminCreate("medicines",
                "{\"code\":\"MDI%s\",\"name\":\"Thuốc đóng %s\",\"status\":\"INACTIVE\"}".formatted(token, token))
                .get("id").asLong();

        mvc.perform(get("/api/v1/medicines")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/medicines").header("Authorization", patientToken()))
                .andExpect(status().isForbidden());

        // bác sĩ: luôn chỉ ACTIVE, kể cả khi cố truyền status=INACTIVE
        JsonNode forDoctor = page(mvc.perform(get("/api/v1/medicines").param("keyword", token)
                .header("Authorization", doctorToken())).andExpect(status().isOk()).andReturn());
        assertEquals(List.of(active), ids(forDoctor));
        assertEquals("Viên", forDoctor.get("content").get(0).get("unit").asText());
        JsonNode doctorForcing = page(mvc.perform(get("/api/v1/medicines").param("keyword", token)
                .param("status", "INACTIVE").header("Authorization", doctorToken()))
                .andExpect(status().isOk()).andReturn());
        assertEquals(List.of(active), ids(doctorForcing));

        // Admin: mặc định thấy cả hai, lọc theo status được
        JsonNode forAdmin = page(mvc.perform(get("/api/v1/medicines").param("keyword", token)
                .header("Authorization", adminToken())).andExpect(status().isOk()).andReturn());
        assertEquals(2, forAdmin.get("totalElements").asInt());
        JsonNode adminInactive = page(mvc.perform(get("/api/v1/medicines").param("keyword", token)
                .param("status", "INACTIVE").header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn());
        assertEquals(List.of(inactive), ids(adminInactive));
    }

    @Test
    @DisplayName("Đường dẫn quản trị danh mục: khách 401; bệnh nhân và bác sĩ 403 ở cả ba danh mục")
    void adminCatalogEndpointsRequireAdmin() throws Exception {
        for (String path : new String[] { "specialties", "medical-services", "medicines" }) {
            String url = "/api/v1/admin/" + path;
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            for (String token : new String[] { patientToken(), doctorToken() }) {
                mvc.perform(get(url).header("Authorization", token)).andExpect(status().isForbidden());
                mvc.perform(get(url + "/1").header("Authorization", token)).andExpect(status().isForbidden());
                mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"name\":\"X\"}")).andExpect(status().isForbidden());
                mvc.perform(put(url + "/1").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}")).andExpect(status().isForbidden());
                mvc.perform(delete(url + "/1").header("Authorization", token)).andExpect(status().isForbidden());
            }
        }
    }

    @Test
    @DisplayName("Người không phải Admin không ghi được vào đường dẫn công khai của danh mục")
    void publicCatalogPathsAreReadOnly() throws Exception {
        mvc.perform(post("/api/v1/specialties").header("Authorization", doctorToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"X\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/medical-services/1").header("Authorization", patientToken()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/medicines").header("Authorization", doctorToken())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // dịch vụ seed không bị ảnh hưởng
        mvc.perform(get("/api/v1/medical-services/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin lọc danh sách quản trị theo trạng thái; trạng thái lạ → 400 ở trường status")
    void adminListFiltersByStatus() throws Exception {
        String token = unique();
        long active = adminCreate("specialties",
                "{\"code\":\"FLA%s\",\"name\":\"Lọc mở %s\"}".formatted(token, token)).get("id").asLong();
        long inactive = adminCreate("specialties",
                "{\"code\":\"FLI%s\",\"name\":\"Lọc đóng %s\",\"status\":\"inactive\"}".formatted(token, token))
                .get("id").asLong();

        JsonNode both = page(mvc.perform(get("/api/v1/admin/specialties").param("keyword", token)
                .header("Authorization", adminToken())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].updatedAt").isNotEmpty())
                .andReturn());
        assertEquals(2, both.get("totalElements").asInt());
        assertEquals(List.of(inactive), ids(page(mvc.perform(get("/api/v1/admin/specialties")
                .param("keyword", token).param("status", "INACTIVE").header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn())));
        assertEquals(List.of(active), ids(page(mvc.perform(get("/api/v1/admin/specialties")
                .param("keyword", token).param("status", "active").header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn())));
        mvc.perform(get("/api/v1/admin/specialties").param("status", "DELETED")
                .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").exists());
    }
}
