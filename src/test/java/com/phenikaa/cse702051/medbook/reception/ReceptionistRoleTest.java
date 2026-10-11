package com.phenikaa.cse702051.medbook.reception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedReceptionist;

/**
 * Vai trò Lễ tân (RECEPTIONIST): Quản trị viên tạo và gán được; lễ tân chỉ vào khu {@code /reception}; gọi thẳng
 * mọi điểm cuối lâm sàng đều bị từ chối ở máy chủ, và lần gọi bệnh án bị từ chối để lại nhật ký có danh tính.
 */
class ReceptionistRoleTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String USERS = "/api/v1/admin/users";
    private static final String PASSWORD = "LeTan#2026x";

    @Autowired
    private PatientRepository patients;
    @Autowired
    private DoctorRepository doctors;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    private static String body(String username, String role, String doctorProfile) {
        return ("{\"username\":\"%s\",\"password\":\"%s\",\"fullName\":\"Lễ tân %s\",\"email\":\"%s@example.com\","
                + "\"phone\":\"0911 222 333\",\"role\":\"%s\"%s}").formatted(username, PASSWORD, username, username,
                        role, doctorProfile == null ? "" : ",\"doctorProfile\":" + doctorProfile);
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("AC-B1.3 Admin tạo lễ tân: 201, không sinh hồ sơ bệnh nhân hay bác sĩ; đăng nhập được, vào được khu lễ tân, "
            + "không vào được khu quản trị")
    void adminCreatesReceptionistWithoutProfiles() throws Exception {
        String username = "lt_" + unique();
        JsonNode created = json(mvc.perform(post(USERS).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(body(username, "receptionist", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.patientId").doesNotExist())
                .andExpect(jsonPath("$.doctorId").doesNotExist()));
        long userId = created.get("id").asLong();
        assertTrue(patients.findByUserId(userId).isEmpty());
        assertTrue(doctors.findByUserId(userId).isEmpty());
        assertEquals(1, countAudits(AuditActions.ENTITY_USERS, userId, AuditActions.USER_CREATE, ADMIN_USER_ID));

        JsonNode session = json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("RECEPTIONIST")));
        String token = "Bearer " + session.get("token").asText();
        mvc.perform(get("/api/v1/reception/khong-ton-tai").header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(get(USERS).header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-B1.3 Lễ tân gửi kèm hồ sơ bác sĩ 400 theo trường; vai trò lạ vẫn 400; không còn lại tài khoản")
    void receptionistWithDoctorProfileIsRejected() throws Exception {
        String username = "lt_" + unique();
        mvc.perform(post(USERS).header("Authorization", adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content(body(username, "RECEPTIONIST",
                        "{\"specialtyId\":1,\"licenseNumber\":\"CCHN-" + unique() + "\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.doctorProfile").isNotEmpty());
        mvc.perform(post(USERS).header("Authorization", adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content(body(username, "NURSE", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.role").isNotEmpty());
        mvc.perform(get(USERS).param("keyword", username).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("AC-B1.3 Gán rồi gỡ vai trò lễ tân qua đổi vai trò; lọc danh sách tài khoản theo vai trò lễ tân")
    void adminAssignsAndRemovesReceptionistRole() throws Exception {
        User account = data.user("ltrole_" + unique(), "Iso#Pass123", "PATIENT");
        String rolesUrl = USERS + "/" + account.getId() + "/roles";

        mvc.perform(put(rolesUrl).header("Authorization", adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"RECEPTIONIST\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("RECEPTIONIST"));
        mvc.perform(get(USERS).param("role", "RECEPTIONIST").param("keyword", account.getUsername())
                .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value(account.getUsername()));
        String asReceptionist = bearer(loginSessions.create(account.getId()).token());
        mvc.perform(get("/api/v1/reception/khong-ton-tai").header("Authorization", asReceptionist))
                .andExpect(status().isNotFound());

        mvc.perform(put(rolesUrl).header("Authorization", adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"PATIENT\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("PATIENT"));
        mvc.perform(get("/api/v1/reception/khong-ton-tai")
                .header("Authorization", bearer(loginSessions.create(account.getId()).token())))
                .andExpect(status().isForbidden());
        mvc.perform(get(USERS).param("role", "RECEPTIONIST").param("keyword", account.getUsername())
                .header("Authorization", adminToken()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("AC-B1.5 Lễ tân gọi thẳng bệnh án, lần khám, đơn thuốc, tệp, hóa đơn, lịch hẹn của bệnh nhân: đều 403; "
            + "lần gọi bệnh án bị từ chối có nhật ký ACCESS_DENIED với danh tính lễ tân")
    void receptionistCannotReadClinicalData() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("ltclin" + key);
        IsolatedPatient patient = data.isolatedPatient("ltclin" + key);
        IsolatedReceptionist receptionist = data.isolatedReceptionist("clin" + key);
        Encounter encounter = data.openEncounter(doctor.doctorId(), patient.patientId());
        Appointment appointment = data.newAppointment(doctor.doctorId(), patient.patientId(),
                AppointmentStatus.BOOKED);
        long recordId = data.record(patient.patientId()).getId();

        String[] paths = {
                "/api/v1/medical-records/" + recordId,
                "/api/v1/patients/" + patient.patientId(),
                "/api/v1/patients/" + patient.patientId() + "/medical-records",
                "/api/v1/encounters/" + encounter.getId(),
                "/api/v1/encounters/" + encounter.getId() + "/prescriptions",
                "/api/v1/encounters/" + encounter.getId() + "/attachments",
                "/api/v1/encounters/" + encounter.getId() + "/invoice",
                "/api/v1/appointments/" + appointment.getId()
        };
        for (String path : paths) {
            mvc.perform(get(path).header("Authorization", receptionist.token()))
                    .andExpect(status().isForbidden());
        }
        // bác sĩ phụ trách vẫn đọc được: 403 ở trên là do vai trò lễ tân, không phải do dữ liệu dựng sai
        mvc.perform(get("/api/v1/medical-records/" + recordId).header("Authorization", doctor.token()))
                .andExpect(status().isOk());

        assertEquals(1, countAudits(AuditActions.ENTITY_MEDICAL_RECORDS, recordId, AuditActions.ACCESS_DENIED,
                receptionist.userId()));
        assertEquals(0, countAudits(AuditActions.ENTITY_MEDICAL_RECORDS, recordId, AuditActions.MEDICAL_RECORD_VIEW,
                receptionist.userId()));
    }
}
