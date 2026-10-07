package com.phenikaa.cse702051.medbook.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * AC-09.5: mặc định tài liệu OpenAPI tắt. Các đường dẫn của nó không được mở công khai (401 khi chưa đăng nhập) và
 * cũng không tồn tại với người đã đăng nhập, kể cả Admin (404).
 */
class OpenApiDisabledByDefaultTest extends AbstractApiTest {

    @Test
    @DisplayName("AC-09.5 Mặc định /v3/api-docs và Swagger UI không truy cập được")
    void openApiIsOffByDefault() throws Exception {
        for (String path : new String[] { "/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html",
                "/swagger-ui/index.html" }) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", adminToken())).andExpect(status().isNotFound());
            mvc.perform(get(path).header("Authorization", patientToken())).andExpect(status().isNotFound());
        }
    }
}
