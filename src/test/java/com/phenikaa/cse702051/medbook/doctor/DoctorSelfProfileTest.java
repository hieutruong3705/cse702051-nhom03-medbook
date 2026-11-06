package com.phenikaa.cse702051.medbook.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;

/**
 * YCCN-43: bác sĩ tự cập nhật phần giới thiệu hiện trên trang công khai. Chỉ sửa được hồ sơ của chính mình (danh tính
 * từ JWT), không đổi được chuyên khoa, số giấy phép hay trạng thái; trang công khai thấy phần giới thiệu mới nhưng
 * vẫn không thấy số giấy phép, điện thoại, tài khoản.
 */
class DoctorSelfProfileTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private DoctorRepository doctors;

    private static final String PROFILE = """
            {"bio":"Bác sĩ thử nghiệm","academicTitle":"Thạc sĩ","yearsOfExperience":10,
             "education":"Đại học Y (mẫu)","expertise":"Tăng huyết áp","workHistory":"Phòng khám MedBook",
             "licenseNumber":"DOI-TRAI-PHEP","specialtyId":2,"isActive":false,"fullName":"Tên khác"}""";

    @Test
    @DisplayName("Bác sĩ sửa phần giới thiệu của mình; trường cấm sửa bị bỏ qua; có nhật ký; trang công khai thấy ngay")
    void doctorUpdatesOwnIntroduction() throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        IsolatedDoctor doctor = data.isolatedDoctor("dsp" + key);
        Doctor before = doctors.findById(doctor.doctorId()).orElseThrow();

        mvc.perform(put("/api/v1/doctors/me/profile")
                .header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(PROFILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doctor.doctorId()))
                .andExpect(jsonPath("$.academicTitle").value("Thạc sĩ"))
                .andExpect(jsonPath("$.yearsOfExperience").value(10))
                .andExpect(jsonPath("$.licenseNumber").value(before.getLicenseNumber()))
                .andExpect(jsonPath("$.fullName").value(before.getFullName()))
                .andExpect(jsonPath("$.isActive").value(true));

        Doctor after = doctors.findById(doctor.doctorId()).orElseThrow();
        assertEquals(before.getLicenseNumber(), after.getLicenseNumber());
        assertEquals(before.getSpecialty() == null, after.getSpecialty() == null, "chuyên khoa không đổi");
        assertEquals(1, countAudits(AuditActions.ENTITY_DOCTORS, doctor.doctorId(),
                AuditActions.DOCTOR_PROFILE_UPDATE, doctor.userId()));

        String json = mvc.perform(get("/api/v1/doctors/{id}", doctor.doctorId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expertise").value("Tăng huyết áp"))
                .andExpect(jsonPath("$.workHistory").value("Phòng khám MedBook"))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode body = JSON.readTree(json);
        for (String internal : new String[] { "licenseNumber", "phone", "userId" }) {
            assertFalse(body.has(internal), "trang công khai không được có " + internal);
        }

        mvc.perform(get("/api/v1/doctors/me/profile").header("Authorization", doctor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.education").value("Đại học Y (mẫu)"));
    }

    @Test
    @DisplayName("Dữ liệu sai → 400 theo từng trường; người không phải bác sĩ → 403; chưa đăng nhập → 401")
    void validatesAndRestrictsToDoctors() throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        IsolatedDoctor doctor = data.isolatedDoctor("dsv" + key);
        mvc.perform(put("/api/v1/doctors/me/profile")
                .header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"yearsOfExperience\":-1,\"academicTitle\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.yearsOfExperience").exists())
                .andExpect(jsonPath("$.details.academicTitle").exists());

        for (String token : new String[] { patientToken(), receptionistToken(),
                adminToken() }) {
            mvc.perform(get("/api/v1/doctors/me/profile").header("Authorization", token))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/doctors/me/profile")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bio\":\"Sửa hộ\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/doctors/me/profile")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/doctors/{id}", doctor.doctorId())
                .header("Authorization", doctor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"Sửa qua đường công khai\"}"))
                .andExpect(status().isForbidden());
    }
}
