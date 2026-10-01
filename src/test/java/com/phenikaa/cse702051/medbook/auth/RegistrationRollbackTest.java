package com.phenikaa.cse702051.medbook.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.service.PatientService;

/**
 * BE-01 (YCCN-01, YCPCN-12): lỗi ở giữa luồng đăng ký (tạo Patient/MedicalRecord) phải hoàn tác cả
 * tài khoản, không để lại User hay gán quyền dở dang. Cần spy nên chạy trong context riêng.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationRollbackTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @MockitoSpyBean
    private PatientService patientService;

    @Test
    @DisplayName("PatientService lỗi giữa chừng → 500, không còn tài khoản mồ côi (User + quyền được rollback)")
    void noOrphanAccountWhenPatientCreationFails() throws Exception {
        doThrow(new IllegalStateException("lỗi giả lập khi tạo hồ sơ bệnh nhân"))
                .when(patientService).createForNewUser(any(), any());

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"be01_rollback","password":"Sup3r#Secret","fullName":"Sẽ Bị Hủy",
                 "email":"be01.rollback@example.com","phone":"0988777555"}
                """))
                .andExpect(status().isInternalServerError());

        assertFalse(users.existsByUsername("be01_rollback"), "tài khoản mồ côi còn sót lại");
        assertFalse(users.existsByEmail("be01.rollback@example.com"));
    }
}
