package com.phenikaa.cse702051.medbook.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * BE-00: một cơ chế danh tính JWT duy nhất; token sai/hết hạn/giả mạo → 401;
 * header {@code X-MedBook-*} do client tự đặt không còn tác dụng.
 */
class JwtIdentityTest extends AbstractApiTest {

    @Test
    @DisplayName("/patients/me chạy đúng với JWT thật (trước đây trả 401 vì đọc userId từ username)")
    void patientMeWorksWithRealJwt() throws Exception {
        mvc.perform(get("/api/v1/patients/me").header("Authorization", patientToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCode").value("BN000001"))
                .andExpect(jsonPath("$.userId").value((int) PATIENT1_USER_ID));
    }

    @Test
    @DisplayName("Header X-MedBook-User-Id không đổi được danh tính")
    void spoofedUserIdHeaderIsIgnored() throws Exception {
        mvc.perform(get("/api/v1/patients/me")
                .header("Authorization", patientToken())
                .header("X-MedBook-User-Id", String.valueOf(PATIENT2_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCode").value("BN000001"));
    }

    @Test
    @DisplayName("Header X-MedBook-Roles không nâng quyền lên ADMIN")
    void spoofedRolesHeaderDoesNotEscalate() throws Exception {
        mvc.perform(get("/api/v1/admin/audit-logs")
                .header("Authorization", patientToken())
                .header("X-MedBook-User-Id", String.valueOf(ADMIN_USER_ID))
                .header("X-MedBook-Roles", "ADMIN,DOCTOR"))
                .andExpect(status().isForbidden());

        // Bác sĩ thêm header PATIENT cũng không vào được /patients/me (chỉ PATIENT thật mới được)
        mvc.perform(get("/api/v1/patients/me")
                .header("Authorization", doctorToken())
                .header("X-MedBook-Roles", "PATIENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Chỉ có header giả, không có token → 401")
    void headersWithoutTokenAreNotAnIdentity() throws Exception {
        mvc.perform(get("/api/v1/patients/me")
                .header("X-MedBook-User-Id", String.valueOf(PATIENT1_USER_ID))
                .header("X-MedBook-Roles", "PATIENT"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token hết hạn → 401 ApiError")
    void expiredTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/patients/me")
                .header("Authorization", expiredToken(PATIENT1_USER_ID, "patient1", "PATIENT")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Token ký bằng khóa khác → 401")
    void forgedTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/admin/invoices")
                .header("Authorization", forgedToken(ADMIN_USER_ID, "admin1", "ADMIN")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token bị sửa nội dung → 401")
    void tamperedTokenIsRejected() throws Exception {
        String token = patientToken();
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        mvc.perform(get("/api/v1/patients/me").header("Authorization", tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token thiếu roles, chuỗi rác và sai scheme → 401")
    void malformedCredentialsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/patients/me").header("Authorization", tokenWithoutRoles(PATIENT1_USER_ID, "patient1")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/me").header("Authorization", "Bearer khong.phai.jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/me").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/me").header("Authorization", "Basic YWRtaW46YWRtaW4="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token hết hạn ở endpoint công khai → vẫn truy cập như khách (200), không 500")
    void expiredTokenOnPublicEndpointIsAnonymous() throws Exception {
        mvc.perform(get("/api/v1/doctors")
                .header("Authorization", expiredToken(PATIENT1_USER_ID, "patient1", "PATIENT")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("JwtUtil trích danh tính một lần: userId số nguyên nhỏ vẫn đọc được thành Long")
    void jwtUtilParsesIdentity() {
        String raw = patientToken().substring("Bearer ".length());
        Optional<AuthenticatedUser> parsed = jwtUtil.parseAuthenticatedUser(raw);

        assertTrue(parsed.isPresent());
        assertEquals(PATIENT1_USER_ID, parsed.get().userId());
        assertEquals("patient1", parsed.get().getName());
        assertEquals(java.util.Set.of("PATIENT"), parsed.get().roles());
        assertEquals(List.of("PATIENT"), jwtUtil.getRoles(raw));
        assertTrue(jwtUtil.parseAuthenticatedUser("khong.phai.jwt").isEmpty());
    }
}
