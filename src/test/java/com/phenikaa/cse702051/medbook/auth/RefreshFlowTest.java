package com.phenikaa.cse702051.medbook.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.repository.*;
import com.phenikaa.cse702051.medbook.support.*;

class RefreshFlowTest extends AbstractApiTest {
    @Autowired AuthSessionRepository sessions;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired UserRepository users;
    @Autowired CapturingPasswordResetNotifier notifier;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String PASSWORD = "TestPwd#2026";
    private JsonNode login() throws Exception {
        String name = "rf" + UUID.randomUUID().toString().replace("-", "");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("username", name, "password", PASSWORD,
                        "fullName", "Refresh Test", "phone", "0901234567", "email", name + "@example.com"))))
                .andExpect(status().isCreated());
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("usernameOrEmail", name, "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refreshToken").isString()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString());
    }
    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("refreshToken", token))));
    }
    private void access(String token, int code) throws Exception {
        mvc.perform(get("/api/v1/patients/me").header("Authorization", "Bearer " + token)).andExpect(status().is(code));
    }
    @Test void rotationAndReplayRevokeTheEntireSession() throws Exception {
        var login = login();
        var response = refresh(login.get("refreshToken").asText()).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        var next = mapper.readTree(response.getResponse().getContentAsString());
        assertNotEquals(login.get("refreshToken"), next.get("refreshToken"));
        assertEquals(login.get("refreshExpiresAt"), next.get("refreshExpiresAt"));
        access(next.get("token").asText(), 200);
        refresh(login.get("refreshToken").asText()).andExpect(status().isUnauthorized());
        access(login.get("token").asText(), 401); access(next.get("token").asText(), 401);
        refresh(next.get("refreshToken").asText()).andExpect(status().isUnauthorized());
        assertTrue(refreshTokens.findAll().stream().allMatch(t -> t.getTokenHash().matches("[0-9a-f]{64}")));
    }
    @Test void logoutRejectsRefresh() throws Exception {
        var login = login();
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + login.get("token").asText()))
                .andExpect(status().isNoContent());
        refresh(login.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }
    @Test void wrongSecretDoesNotRevokeVictimSession() throws Exception {
        var login = login(); var raw = login.get("refreshToken").asText();
        refresh(raw.substring(0, 37) + "A".repeat(43)).andExpect(status().isUnauthorized());
        refresh(raw).andExpect(status().isOk());
    }
    @Test void expiredSessionCannotRefresh() throws Exception {
        var login = login(); var raw = login.get("refreshToken").asText();
        var s = sessions.findById(raw.substring(0,36)).orElseThrow();
        s.setExpiresAt(Instant.now().minusSeconds(1)); sessions.save(s);
        refresh(raw).andExpect(status().isUnauthorized());
    }
    @Test void invalidBodyIs400() throws Exception {
        refresh("invalid").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
    @Test void passwordChangePreventsRefresh() throws Exception {
        var login = login();
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + login.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"oldPassword\":\"TestPwd#2026\",\"newPassword\":\"Changed#2026\"}"))
                .andExpect(status().isNoContent());
        refresh(login.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }
    @Test void resetPreventsRefresh() throws Exception {
        var login = login(); var email = login.get("email").asText();
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("email", email)))).andExpect(status().isAccepted());
        var reset = notifier.lastTokenFor(email).orElseThrow();
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("token", reset, "newPassword", "ResetPwd#2026"))))
                .andExpect(status().isNoContent());
        refresh(login.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }
    @Test void changedAccountVersionPreventsRefresh() throws Exception {
        var login = login(); var user = users.findById(login.get("userId").asLong()).orElseThrow();
        user.setTokenVersion(user.getTokenVersion()+1); users.save(user);
        refresh(login.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }
    @Test void disabledAccountPreventsRefresh() throws Exception {
        var login = login(); var user = users.findById(login.get("userId").asLong()).orElseThrow();
        user.setStatus("LOCKED"); users.save(user);
        refresh(login.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }
    @Test void simultaneousRefreshAllowsOneRotationThenRevokesReplay() throws Exception {
        var login = login(); var raw = login.get("refreshToken").asText();
        var pool = Executors.newFixedThreadPool(2); var gate = new CountDownLatch(1);
        try {
            Callable<Integer> task = () -> { gate.await(); return refresh(raw).andReturn().getResponse().getStatus(); };
            var one = pool.submit(task); var two = pool.submit(task); gate.countDown();
            var codes = new java.util.ArrayList<>(java.util.List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)));
            codes.sort(Integer::compareTo); assertEquals(java.util.List.of(200,401), codes);
            access(login.get("token").asText(),401);
        } finally { pool.shutdownNow(); }
    }
}
