package com.phenikaa.cse702051.medbook.patient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedReceptionist;

/**
 * YCCN-42: bệnh nhân tự khai chiều cao, cân nặng, số thẻ BHYT, bệnh nền, tiền sử gia đình và liên hệ khẩn cấp. Bác sĩ
 * phụ trách đọc được; quản trị viên và lễ tân chỉ nhận trường hành chính, không bao giờ thấy các trường sức khỏe.
 */
class PatientHealthProfileTest extends AbstractApiTest {

    private static final String[] HEALTH_FIELDS = { "heightCm", "weightKg", "healthInsuranceNumber",
            "chronicConditions", "familyHistory", "bloodType", "allergies" };

    private static final String HEALTH = """
            {"heightCm":172,"weightKg":68.25,"healthInsuranceNumber":"dn4010123456789",
             "chronicConditions":"  Tăng huyết áp nhẹ  ","familyHistory":"Mẹ đái tháo đường",
             "emergencyContactName":"Người thân mẫu","emergencyContactPhone":"0911222333","bloodType":"o+"}""";

    @Test
    @DisplayName("Bệnh nhân khai thông tin sức khỏe; bác sĩ phụ trách đọc được; quản trị viên và lễ tân không thấy trường sức khỏe")
    void selfReportedHealthDataReachesOnlyPatientAndResponsibleDoctor() throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        IsolatedPatient patient = data.isolatedPatient("hp" + key);
        IsolatedDoctor doctor = data.isolatedDoctor("hp" + key);
        IsolatedReceptionist receptionist = data.isolatedReceptionist("hp" + key);
        data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.BOOKED);

        mvc.perform(put("/api/v1/patients/me")
                .header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(HEALTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightCm").value(172))
                .andExpect(jsonPath("$.weightKg").value(68.3))
                .andExpect(jsonPath("$.healthInsuranceNumber").value("DN4010123456789"))
                .andExpect(jsonPath("$.chronicConditions").value("Tăng huyết áp nhẹ"))
                .andExpect(jsonPath("$.familyHistory").value("Mẹ đái tháo đường"))
                .andExpect(jsonPath("$.emergencyContactName").value("Người thân mẫu"))
                .andExpect(jsonPath("$.bloodType").value("O+"));

        mvc.perform(get("/api/v1/patients/{id}", patient.patientId()).header("Authorization", doctor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chronicConditions").value("Tăng huyết áp nhẹ"))
                .andExpect(jsonPath("$.heightCm").value(172));

        String admin = mvc.perform(get("/api/v1/patients/{id}", patient.patientId())
                .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reception = mvc.perform(get("/api/v1/reception/patients/{id}", patient.patientId())
                .header("Authorization", receptionist.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (String field : HEALTH_FIELDS) {
            assertFalse(admin.contains("\"" + field + "\""), "quản trị viên không được thấy " + field);
            assertFalse(reception.contains("\"" + field + "\""), "lễ tân không được thấy " + field);
        }
        assertFalse(reception.contains("Tăng huyết áp"));
    }

    @Test
    @DisplayName("Giá trị ngoài khoảng hoặc sai định dạng → 400 theo từng trường; chuỗi rỗng xóa giá trị đã khai")
    void validatesHealthFieldsAndClearsWithEmptyString() throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        IsolatedPatient patient = data.isolatedPatient("hv" + key);

        mvc.perform(put("/api/v1/patients/me")
                .header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"heightCm\":10,\"weightKg\":500,\"healthInsuranceNumber\":\"123\",\"bloodType\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.heightCm").exists())
                .andExpect(jsonPath("$.details.weightKg").exists())
                .andExpect(jsonPath("$.details.healthInsuranceNumber").exists())
                .andExpect(jsonPath("$.details.bloodType").exists());

        mvc.perform(put("/api/v1/patients/me")
                .header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chronicConditions\":\"Hen phế quản\",\"healthInsuranceNumber\":\"GD4010000000009\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chronicConditions").value("Hen phế quản"));
        mvc.perform(put("/api/v1/patients/me")
                .header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chronicConditions\":\"\",\"healthInsuranceNumber\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chronicConditions").doesNotExist())
                .andExpect(jsonPath("$.healthInsuranceNumber").doesNotExist());
    }
}
