package com.phenikaa.cse702051.medbook.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.phenikaa.cse702051.medbook.repository.AuthSessionRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

class PersistentSessionTest extends AbstractApiTest {
    @Autowired AuthSessionRepository sessions;
    private void statusOf(String raw, int status) throws Exception {
        mvc.perform(get("/api/v1/patients/me").header("Authorization", "Bearer " + raw))
                .andExpect(status().is(status));
    }
    @Test void oldTokenWithoutSessionIsRejected() throws Exception {
        statusOf(jwtUtil.generateToken(PATIENT1_USER_ID, "patient1", List.of("PATIENT")), 401);
    }
    @Test void expiredSessionRejectsUnexpiredAccessToken() throws Exception {
        var token = loginSessions.create(PATIENT1_USER_ID);
        statusOf(token.token(), 200);
        var claims = jwtUtil.parseClaims(token.token()).orElseThrow();
        var session = sessions.findById(claims.sessionId()).orElseThrow();
        session.setExpiresAt(Instant.now().minusSeconds(1)); sessions.save(session);
        statusOf(token.token(), 401);
    }
    @Test void sessionCannotBeUsedForAnotherUser() throws Exception {
        var token = loginSessions.create(PATIENT1_USER_ID);
        var claims = jwtUtil.parseClaims(token.token()).orElseThrow();
        var forgedIdentity = jwtUtil.issueToken(PATIENT2_USER_ID, "patient2", List.of("PATIENT"),
                claims.version(), claims.sessionId(), Instant.now().plusSeconds(300));
        statusOf(forgedIdentity.token(), 401);
    }
    @Test void logoutRevokesAllTokensInThatSessionOnly() throws Exception {
        var first = loginSessions.create(PATIENT1_USER_ID);
        var otherSession = loginSessions.create(PATIENT1_USER_ID);
        var claims = jwtUtil.parseClaims(first.token()).orElseThrow();
        var second = jwtUtil.issueToken(PATIENT1_USER_ID, "patient1", List.of("PATIENT"),
                claims.version(), claims.sessionId(), Instant.now().plusSeconds(300));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + first.token()))
                .andExpect(status().isNoContent());
        statusOf(first.token(), 401); statusOf(second.token(), 401);
        statusOf(otherSession.token(), 200);
    }
}
