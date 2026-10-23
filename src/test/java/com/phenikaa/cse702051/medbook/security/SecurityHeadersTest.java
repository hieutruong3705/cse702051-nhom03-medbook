package com.phenikaa.cse702051.medbook.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * BM12 (thẻ tiêu đề bảo mật) và BM9 (rò rỉ thông tin qua thông báo lỗi): mọi phản hồi, kể cả lỗi, mang đủ header bảo
 * mật; lỗi không lộ stack trace, tên lớp hay câu SQL.
 */
class SecurityHeadersTest extends AbstractApiTest {

    @Test
    @DisplayName("BM12 Phản hồi API (kể cả 401) có CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy")
    void apiResponsesCarrySecurityHeaders() throws Exception {
        for (var request : java.util.List.of(
                get("/api/v1/specialties"),
                get("/api/v1/admin/users"),
                get("/api/v1/users/me").header("Authorization", patientToken()))) {
            mvc.perform(request)
                    .andExpect(header().string("Content-Security-Policy", Matchers.allOf(
                            Matchers.containsString("default-src 'self'"),
                            Matchers.containsString("script-src 'self'"),
                            Matchers.containsString("object-src 'none'"),
                            Matchers.containsString("frame-ancestors 'none'"))))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Permissions-Policy", Matchers.containsString("camera=()")));
        }
    }

    @Test
    @DisplayName("BM12 Strict-Transport-Security chỉ gửi trên HTTPS, thời hạn một năm, gồm cả tên miền con")
    void hstsIsSentOverHttpsOnly() throws Exception {
        mvc.perform(get("/api/v1/specialties").secure(true))
                .andExpect(header().string("Strict-Transport-Security", Matchers.allOf(
                        Matchers.containsString("max-age=31536000"),
                        Matchers.containsStringIgnoringCase("includeSubDomains"))));
        mvc.perform(get("/api/v1/specialties"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("BM9 Lỗi không lộ thông tin nội bộ: không stack trace, không tên lớp, không câu SQL")
    void errorsDoNotLeakInternals() throws Exception {
        String[] bodies = {
                mvc.perform(get("/api/v1/doctors/abc"))
                        .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(),
                mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{sai json"))
                        .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(),
                mvc.perform(get("/api/v1/doctors").param("keyword", "' OR 1=1 --"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalElements").value(0))
                        .andReturn().getResponse().getContentAsString(),
                mvc.perform(get("/api/v1/khong-ton-tai").header("Authorization", patientToken()))
                        .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString() };
        for (String body : bodies) {
            for (String leak : new String[] { "Exception", "java.", "org.hibernate", "org.springframework",
                    "select ", "SELECT ", "\tat ", "trace" }) {
                org.junit.jupiter.api.Assertions.assertFalse(body.contains(leak),
                        "phản hồi lỗi lộ \"" + leak + "\": " + body);
            }
        }
    }
}
