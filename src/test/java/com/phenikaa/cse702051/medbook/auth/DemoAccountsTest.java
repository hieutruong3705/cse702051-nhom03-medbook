package com.phenikaa.cse702051.medbook.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * Tài khoản demo trong {@code data.sql} (dev/H2): cả 5 tài khoản dùng chung mật khẩu demo
 * {@value #DEMO_PASSWORD}. Test này bảo đảm thông tin ghi trong README/tài liệu luôn đăng nhập được.
 */
class DemoAccountsTest extends AbstractApiTest {

    static final String DEMO_PASSWORD = "MedBook@2026";

    @ParameterizedTest(name = "{0} đăng nhập được với vai trò {1}")
    @CsvSource({
            "admin1,ADMIN",
            "doctor1,DOCTOR",
            "doctor2,DOCTOR",
            "patient1,PATIENT",
            "patient2,PATIENT"
    })
    @DisplayName("Tài khoản demo đăng nhập được bằng mật khẩu demo và có đúng vai trò")
    void demoAccountsCanLogIn(String username, String role) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(username, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value(role))
                .andReturn();

        JsonNode login = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertEquals(username, login.get("username").asText());
        if ("DOCTOR".equals(role)) {
            assertEquals(true, login.get("doctorId").asLong() > 0);
        }
        if ("PATIENT".equals(role)) {
            assertEquals(true, login.get("patientId").asLong() > 0);
        }
        // token dùng được ngay trên một API theo vai trò
        String path = switch (role) {
            case "ADMIN" -> "/api/v1/admin/invoices";
            case "DOCTOR" -> "/api/v1/patients";
            default -> "/api/v1/patients/me";
        };
        mvc.perform(get(path).header("Authorization", "Bearer " + login.get("token").asText()))
                .andExpect(status().isOk());
    }
}
