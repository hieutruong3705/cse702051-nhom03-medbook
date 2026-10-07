package com.phenikaa.cse702051.medbook.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * BM10 (dò mật khẩu): một địa chỉ IP đăng nhập sai quá ngưỡng thì nhận 429 kèm {@code Retry-After}, kể cả khi sau
 * đó nhập đúng mật khẩu; IP khác không bị ảnh hưởng; nhật ký ghi nhận đúng một lần cho mỗi đợt.
 */
@TestPropertySource(properties = {
        "medbook.security.login-rate-limit.max-failures=3",
        "medbook.security.login-rate-limit.window-seconds=120" })
class LoginRateLimitTest extends AbstractApiTest {

    private static final String PASSWORD = "Gioi#Han2026";

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    private ResultActions login(String ip, String identifier, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password)));
    }

    private long rateLimitAudits(String ip) {
        return auditLogs.findAll().stream()
                .filter(audit -> AuditActions.LOGIN_RATE_LIMITED.equals(audit.getActionCode()))
                .filter(audit -> ip.equals(audit.getIpAddress()))
                .count();
    }

    @Test
    @DisplayName("Đăng nhập sai quá ngưỡng từ một IP: 429 TOO_MANY_REQUESTS kèm Retry-After, đúng mật khẩu cũng bị từ chối")
    void repeatedFailuresFromOneIpAreRateLimited() throws Exception {
        String username = "rl_" + unique();
        data.user(username, PASSWORD, "PATIENT");
        String attacker = "203.0.113.10";

        // ba lần sai trên ba tài khoản khác nhau (dò rải) vẫn bị tính chung cho một IP
        login(attacker, "khong_ton_tai_" + unique(), "Sai#Mat1khau").andExpect(status().isUnauthorized());
        login(attacker, username, "Sai#Mat1khau").andExpect(status().isUnauthorized());
        login(attacker, "admin1", "Sai#Mat1khau").andExpect(status().isUnauthorized());

        String retryAfter = login(attacker, username, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.details.retryAfterSeconds").isNotEmpty())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(header().exists("Retry-After"))
                .andReturn().getResponse().getHeader("Retry-After");
        long seconds = Long.parseLong(retryAfter);
        assertTrue(seconds >= 1 && seconds <= 120, "Retry-After = " + retryAfter);

        login(attacker, username, PASSWORD).andExpect(status().isTooManyRequests());
        assertEquals(1, rateLimitAudits(attacker), "mỗi đợt chạm ngưỡng chỉ ghi nhật ký một lần");
    }

    @Test
    @DisplayName("Giới hạn tính theo từng IP: IP khác vẫn đăng nhập được; đăng nhập đúng không bị tính là lần sai")
    void otherClientsAreNotAffected() throws Exception {
        String username = "rl_" + unique();
        data.user(username, PASSWORD, "PATIENT");
        String attacker = "203.0.113.20";
        String honest = "203.0.113.21";

        for (int i = 0; i < 3; i++) {
            login(attacker, "khong_ton_tai_" + unique(), "Sai#Mat1khau").andExpect(status().isUnauthorized());
        }
        login(attacker, username, PASSWORD).andExpect(status().isTooManyRequests());

        for (int i = 0; i < 5; i++) {
            login(honest, username, PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }
        login(honest, username, "Sai#Mat1khau").andExpect(status().isUnauthorized());
        login(honest, username, PASSWORD).andExpect(status().isOk());
        assertEquals(0, rateLimitAudits(honest));
    }
}
