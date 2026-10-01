package com.phenikaa.cse702051.medbook.patient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.AuditLog;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.ApiTestData;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * BE-02: kiểm soát truy cập bệnh án theo từng bản ghi (YCCN-07) và audit mọi lần đọc (YCCN-23).
 *
 * <p>Dựng sẵn: bác sĩ 1 phụ trách bệnh nhân 1 (lịch hẹn chưa hủy); bác sĩ 2 phụ trách bệnh nhân 2;
 * bệnh nhân X chỉ có lịch hẹn ĐÃ HỦY với bác sĩ 1; bệnh nhân Y chỉ có lần khám (không có lịch) với
 * bác sĩ 1.
 */
class MedicalRecordAccessTest extends AbstractApiTest {

    private static final String UA = "JUnit-Agent/1.0";

    private MedicalRecord record1;
    private MedicalRecord record2;
    private MedicalRecord recordCancelledOnly;
    private MedicalRecord recordEncounterOnly;

    @BeforeEach
    void fixtures() {
        record1 = data.record(ApiTestData.PATIENT1_ID);
        record2 = data.record(ApiTestData.PATIENT2_ID);
        data.appointment(ApiTestData.DOCTOR1_ID, ApiTestData.PATIENT1_ID, AppointmentStatus.BOOKED);
        data.appointment(ApiTestData.DOCTOR2_ID, ApiTestData.PATIENT2_ID, AppointmentStatus.BOOKED);

        Patient cancelledOnly = data.extraPatient("TEST-CANCELLED");
        data.appointment(ApiTestData.DOCTOR1_ID, cancelledOnly.getId(), AppointmentStatus.CANCELLED);
        recordCancelledOnly = data.record(cancelledOnly.getId());

        Patient encounterOnly = data.extraPatient("TEST-ENCOUNTER");
        recordEncounterOnly = data.record(encounterOnly.getId());
        data.encounter(ApiTestData.DOCTOR1_ID, recordEncounterOnly.getId());
    }

    private long views(MedicalRecord record, long actorUserId) {
        return countAudits(AuditActions.ENTITY_MEDICAL_RECORDS, record.getId(),
                AuditActions.MEDICAL_RECORD_VIEW, actorUserId);
    }

    private long denials(MedicalRecord record, long actorUserId) {
        return countAudits(AuditActions.ENTITY_MEDICAL_RECORDS, record.getId(),
                AuditActions.ACCESS_DENIED, actorUserId);
    }

    @Test
    @DisplayName("Bệnh nhân đọc bệnh án của mình qua /me → 200 và ghi audit đầy đủ user, record, IP, UA, thời gian")
    void patientReadsOwnRecordViaMe() throws Exception {
        long before = views(record1, PATIENT1_USER_ID);
        LocalDateTime start = LocalDateTime.now().minusSeconds(2);

        mvc.perform(get("/api/v1/medical-records/me")
                .header("Authorization", patientToken())
                .header("User-Agent", UA)
                .with(request -> {
                    request.setRemoteAddr("10.1.2.3");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(record1.getId()))
                .andExpect(jsonPath("$.recordCode").value("MR%06d".formatted(ApiTestData.PATIENT1_ID)))
                .andExpect(jsonPath("$.patientId").value((int) ApiTestData.PATIENT1_ID));

        assertEquals(before + 1, views(record1, PATIENT1_USER_ID));
        AuditLog log = auditsOf(AuditActions.ENTITY_MEDICAL_RECORDS, record1.getId()).getLast();
        assertEquals(AuditActions.MEDICAL_RECORD_VIEW, log.getActionCode());
        assertEquals(PATIENT1_USER_ID, log.getActorUser().getId());
        assertEquals(record1.getId(), log.getEntityId());
        assertEquals("10.1.2.3", log.getIpAddress());
        assertEquals(UA, log.getUserAgent());
        assertFalse(log.getCreatedAt().isBefore(start));
    }

    @Test
    @DisplayName("metadata audit được lưu thành JSON hợp lệ, đọc lại đúng nội dung")
    void auditMetadataRoundTripsAsJson() throws Exception {
        mvc.perform(get("/api/v1/medical-records/" + record1.getId()).header("Authorization", patientToken()))
                .andExpect(status().isOk());

        AuditLog log = auditsOf(AuditActions.ENTITY_MEDICAL_RECORDS, record1.getId()).getLast();
        assertNotNull(log.getMetadataJson());
        JsonNode metadata = new ObjectMapper().readTree(log.getMetadataJson());
        assertTrue(metadata.isObject(), "metadata phải là đối tượng JSON, nhận: " + log.getMetadataJson());
        assertEquals("VIEW_MEDICAL_RECORD", metadata.get("accessType").asText());
        assertEquals(ApiTestData.PATIENT1_ID, metadata.get("patientId").asLong());
    }

    @Test
    @DisplayName("Bệnh nhân đọc bệnh án của mình theo ID → 200 + audit")
    void patientReadsOwnRecordById() throws Exception {
        long before = views(record1, PATIENT1_USER_ID);

        mvc.perform(get("/api/v1/medical-records/" + record1.getId()).header("Authorization", patientToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCode").value(record1.getRecordCode()));

        assertEquals(before + 1, views(record1, PATIENT1_USER_ID));
    }

    @Test
    @DisplayName("Bệnh nhân B đọc bệnh án của bệnh nhân A → 403 và ghi ACCESS_DENIED, không trả dữ liệu")
    void patientCannotReadAnotherPatientsRecord() throws Exception {
        long deniedBefore = denials(record1, PATIENT2_USER_ID);
        long viewsBefore = views(record1, PATIENT2_USER_ID);

        mvc.perform(get("/api/v1/medical-records/" + record1.getId()).header("Authorization", patient2Token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.recordCode").doesNotExist());

        assertEquals(deniedBefore + 1, denials(record1, PATIENT2_USER_ID));
        assertEquals(viewsBefore, views(record1, PATIENT2_USER_ID), "lần bị từ chối không được tính là đã xem");
    }

    @Test
    @DisplayName("Admin đọc bệnh án → 403 (không mặc nhiên đọc nội dung nhạy cảm) và ghi ACCESS_DENIED")
    void adminCannotReadRecord() throws Exception {
        long before = denials(record1, ADMIN_USER_ID);

        mvc.perform(get("/api/v1/medical-records/" + record1.getId()).header("Authorization", adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Quản trị viên")));

        assertEquals(before + 1, denials(record1, ADMIN_USER_ID));
    }

    @Test
    @DisplayName("Bác sĩ phụ trách đọc được bệnh án → 200 + audit; bác sĩ không phụ trách → 403 + ACCESS_DENIED")
    void doctorScopeIsEnforced() throws Exception {
        // Bác sĩ và bệnh nhân dựng riêng: không phụ thuộc lịch hẹn mà test khác tạo trên tài khoản seed.
        var docA = data.isolatedDoctor("scope_a");
        var docB = data.isolatedDoctor("scope_b");
        Patient patA = data.extraPatient("ISO-SCOPE-A");
        Patient patB = data.extraPatient("ISO-SCOPE-B");
        MedicalRecord recA = data.record(patA.getId());
        MedicalRecord recB = data.record(patB.getId());
        data.appointment(docA.doctorId(), patA.getId(), AppointmentStatus.BOOKED);
        data.appointment(docB.doctorId(), patB.getId(), AppointmentStatus.BOOKED);

        long viewsBefore = views(recA, docA.userId());
        long deniedBefore = denials(recA, docB.userId());

        mvc.perform(get("/api/v1/medical-records/" + recA.getId()).header("Authorization", docA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recA.getId()));
        mvc.perform(get("/api/v1/medical-records/" + recA.getId()).header("Authorization", docB.token()))
                .andExpect(status().isForbidden());

        assertEquals(viewsBefore + 1, views(recA, docA.userId()));
        assertEquals(deniedBefore + 1, denials(recA, docB.userId()));

        // và ngược lại: bác sĩ B đọc được bệnh nhân B, bác sĩ A thì không
        mvc.perform(get("/api/v1/medical-records/" + recB.getId()).header("Authorization", docB.token()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/medical-records/" + recB.getId()).header("Authorization", docA.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lịch hẹn đã hủy không tạo quan hệ phụ trách; lần khám thì có")
    void cancelledAppointmentDoesNotGrantAccessButEncounterDoes() throws Exception {
        mvc.perform(get("/api/v1/medical-records/" + recordCancelledOnly.getId())
                .header("Authorization", doctorToken()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/medical-records/" + recordEncounterOnly.getId())
                .header("Authorization", doctorToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /patients/{id}/medical-records: bác sĩ phụ trách 200 + audit; bác sĩ khác, Admin 403; bệnh nhân bị chặn ở route")
    void medicalRecordByPatientId() throws Exception {
        var docA = data.isolatedDoctor("byid_a");
        var docB = data.isolatedDoctor("byid_b");
        Patient patient = data.extraPatient("ISO-BYID");
        MedicalRecord record = data.record(patient.getId());
        data.appointment(docA.doctorId(), patient.getId(), AppointmentStatus.BOOKED);

        long viewsBefore = views(record, docA.userId());
        String url = "/api/v1/patients/" + patient.getId() + "/medical-records";

        mvc.perform(get(url).header("Authorization", docA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(record.getId()));
        assertEquals(viewsBefore + 1, views(record, docA.userId()));

        mvc.perform(get(url).header("Authorization", docB.token())).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", adminToken())).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", patientToken())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/patients/999999/medical-records").header("Authorization", docA.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Bệnh án không tồn tại → 404")
    void unknownRecord() throws Exception {
        mvc.perform(get("/api/v1/medical-records/999999").header("Authorization", patientToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Không còn API ghi bệnh án bằng entity thô (POST /medical-records)")
    void rawCreateEndpointIsGone() throws Exception {
        String body = "{\"patient\":{\"id\":1},\"recordCode\":\"HACK\",\"status\":\"ACTIVE\"}";

        mvc.perform(post("/api/v1/medical-records").header("Authorization", patientToken())
                .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/medical-records/" + record1.getId()).header("Authorization", patientToken())
                .contentType("application/json").content(body))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Mỗi lần đọc đều có một bản ghi audit riêng (100% lần xem có log)")
    void everyReadIsAudited() throws Exception {
        long before = views(record1, PATIENT1_USER_ID);

        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/v1/medical-records/me").header("Authorization", patientToken()))
                    .andExpect(status().isOk());
        }

        assertEquals(before + 5, views(record1, PATIENT1_USER_ID));
        List<AuditLog> all = auditsOf(AuditActions.ENTITY_MEDICAL_RECORDS, record1.getId());
        assertTrue(all.stream().allMatch(a -> a.getCreatedAt() != null && a.getActionCode() != null));
    }
}
