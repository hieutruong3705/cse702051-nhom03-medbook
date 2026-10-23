package com.phenikaa.cse702051.medbook.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * Các quy ước chung của API: điểm kiểm tra sức khỏe không cần đăng nhập, mã định danh yêu cầu trong tiêu đề và
 * trong thân lỗi, tiêu đề {@code Location} khi tạo mới.
 */
class ApiConventionsTest extends AbstractApiTest {

    private static final String REQUEST_ID = "X-Request-Id";

    @Test
    @DisplayName("GET /api/v1/health và /api/v1/system/status trả 200 khi chưa đăng nhập")
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/system/status")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mỗi phản hồi có X-Request-Id; thân lỗi 401, 403, 404, 400 mang đúng mã đó")
    void errorsCarryTheRequestId() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(header().exists(REQUEST_ID));

        for (var request : java.util.List.of(
                get("/api/v1/admin/users"),
                get("/api/v1/admin/users").header("Authorization", patientToken()),
                get("/api/v1/doctors/99999999"),
                post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"usernameOrEmail\":"))) {
            MvcResult result = mvc.perform(request).andExpect(status().is4xxClientError()).andReturn();
            String headerId = result.getResponse().getHeader(REQUEST_ID);
            assertNotNull(headerId, "thiếu X-Request-Id");
            assertEquals(headerId, JsonPath.read(result.getResponse().getContentAsString(), "$.requestId"),
                    "requestId trong thân lỗi phải trùng tiêu đề (HTTP " + result.getResponse().getStatus() + ")");
        }
    }

    @Test
    @DisplayName("Mã do phía gọi gửi lên được dùng lại khi hợp lệ; chuỗi lạ bị thay bằng mã mới")
    void incomingRequestIdIsReusedOnlyWhenSafe() throws Exception {
        mvc.perform(get("/api/v1/health").header(REQUEST_ID, "kiem-thu-0001"))
                .andExpect(header().string(REQUEST_ID, "kiem-thu-0001"));

        String forged = "abc\r\nSet-Cookie: x=1 <script>";
        String replaced = mvc.perform(get("/api/v1/health").header(REQUEST_ID, forged))
                .andReturn().getResponse().getHeader(REQUEST_ID);
        assertNotEquals(forged, replaced);
        assertTrue(replaced.matches("[A-Za-z0-9._-]{8,64}"), replaced);

        String first = mvc.perform(get("/api/v1/health")).andReturn().getResponse().getHeader(REQUEST_ID);
        String second = mvc.perform(get("/api/v1/health")).andReturn().getResponse().getHeader(REQUEST_ID);
        assertNotEquals(first, second, "mỗi yêu cầu một mã riêng");
    }

    @Test
    @DisplayName("Tạo mới trả 201 kèm Location trỏ tới bản ghi vừa tạo, mở được bằng GET")
    void createdResponsesPointToTheNewResource() throws Exception {
        String code = "QU" + (System.nanoTime() % 1_000_000);
        MvcResult created = mvc.perform(post("/api/v1/admin/specialties")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\",\"name\":\"Chuyên khoa quy ước %s\"}".formatted(code, code)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.startsWith("/api/v1/admin/specialties/")))
                .andReturn();

        String location = created.getResponse().getHeader("Location");
        Number id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        assertEquals("/api/v1/admin/specialties/" + id, location);
        mvc.perform(get(location).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }
}
