package com.phenikaa.cse702051.medbook.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.service.MedicalRecordService;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * BE-04 (YCCN-17, 18, 19): luồng lần khám gắn trạng thái lịch hẹn, quyền theo bản ghi và audit.
 * Mỗi test dùng bác sĩ/bệnh nhân riêng (khóa khác nhau) nên không phụ thuộc dữ liệu của test khác.
 */
class EncounterFlowTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private EncounterRepository encounters;

    @Autowired
    private MedicalRecordService medicalRecords;

    // ---------- helpers ----------

    private ResultActions startExam(String token, long appointmentId) throws Exception {
        return mvc.perform(post("/api/v1/encounters").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentId\":%d,\"chiefComplaint\":\"Đau đầu 3 ngày\"}".formatted(appointmentId)));
    }

    private long startExamOk(String token, long appointmentId) throws Exception {
        MvcResult result = startExam(token, appointmentId).andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions update(String token, long encounterId, String json) throws Exception {
        return mvc.perform(put("/api/v1/encounters/" + encounterId).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions read(String token, long encounterId) throws Exception {
        return mvc.perform(get("/api/v1/encounters/" + encounterId).header("Authorization", token));
    }

    private AppointmentStatus statusOf(long appointmentId) {
        return appointments.findById(appointmentId).orElseThrow().getStatus();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    // ---------- bắt đầu khám ----------

    @Test
    @DisplayName("Bác sĩ phụ trách bắt đầu khám: 201, lịch → IN_PROGRESS, bệnh án suy ra từ bệnh nhân, bác sĩ từ JWT")
    void startExamination() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("enc_start");
        IsolatedPatient pat = data.isolatedPatient("enc_start");
        Appointment appointment = data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.BOOKED);

        // client cố tình gửi doctorId/medicalRecordId giả: phải bị bỏ qua
        MvcResult result = mvc.perform(post("/api/v1/encounters").header("Authorization", doc.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"appointmentId":%d,"chiefComplaint":"  Ho kéo dài  ","doctorId":%d,"medicalRecordId":1}
                        """.formatted(appointment.getId(), ApiTestData.DOCTOR1_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.doctorId").value(doc.doctorId()))
                .andExpect(jsonPath("$.patientId").value(pat.patientId()))
                .andExpect(jsonPath("$.chiefComplaint").value("Ho kéo dài"))
                .andReturn();

        JsonNode created = body(result);
        MedicalRecord record = medicalRecords.getOrCreateByPatientId(pat.patientId());
        assertEquals(record.getId(), created.get("medicalRecordId").asLong());
        assertEquals(AppointmentStatus.IN_PROGRESS, statusOf(appointment.getId()));
        assertEquals(1, countAudits(AuditActions.ENTITY_ENCOUNTERS, created.get("id").asLong(),
                AuditActions.ENCOUNTER_CREATE, doc.userId()));
    }

    @Test
    @DisplayName("Bác sĩ khác không bắt đầu khám cho lịch của bác sĩ A: 403, lịch vẫn BOOKED, có audit")
    void otherDoctorCannotStart() throws Exception {
        IsolatedDoctor owner = data.isolatedDoctor("enc_owner1");
        IsolatedDoctor intruder = data.isolatedDoctor("enc_intruder1");
        IsolatedPatient pat = data.isolatedPatient("enc_intr");
        Appointment appointment = data.newAppointment(owner.doctorId(), pat.patientId(), AppointmentStatus.BOOKED);

        startExam(intruder.token(), appointment.getId()).andExpect(status().isForbidden());

        assertEquals(AppointmentStatus.BOOKED, statusOf(appointment.getId()));
        assertFalse(encounters.existsByAppointmentId(appointment.getId()));
        assertEquals(1, countAudits(AuditActions.ENTITY_APPOINTMENTS, appointment.getId(),
                AuditActions.ACCESS_DENIED, intruder.userId()));
    }

    @Test
    @DisplayName("Bệnh nhân, Admin bắt đầu khám → 403; lịch không tồn tại → 404; thiếu appointmentId → 400")
    void onlyDoctorStartsAndInputIsValidated() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("enc_roles");
        IsolatedPatient pat = data.isolatedPatient("enc_roles");
        Appointment appointment = data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.BOOKED);

        startExam(pat.token(), appointment.getId()).andExpect(status().isForbidden());
        startExam(adminToken(), appointment.getId()).andExpect(status().isForbidden());
        startExam(doc.token(), 999_999L).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/encounters").header("Authorization", doc.token())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.appointmentId").exists());
        assertEquals(AppointmentStatus.BOOKED, statusOf(appointment.getId()));
    }

    @Test
    @DisplayName("Bắt đầu khám hai lần → 409; lịch đã hủy/đã hoàn thành → 422, trạng thái không đổi")
    void startOnlyFromBookedAndOnlyOnce() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("enc_twice");
        IsolatedPatient pat = data.isolatedPatient("enc_twice");

        Appointment booked = data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.BOOKED);
        startExamOk(doc.token(), booked.getId());
        startExam(doc.token(), booked.getId()).andExpect(status().isConflict());
        assertEquals(AppointmentStatus.IN_PROGRESS, statusOf(booked.getId()));

        Appointment cancelled = data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.CANCELLED);
        startExam(doc.token(), cancelled.getId()).andExpect(status().isUnprocessableContent());
        assertEquals(AppointmentStatus.CANCELLED, statusOf(cancelled.getId()));

        Appointment completed = data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.COMPLETED);
        startExam(doc.token(), completed.getId()).andExpect(status().isUnprocessableContent());
        assertEquals(AppointmentStatus.COMPLETED, statusOf(completed.getId()));
    }

    // ---------- cập nhật / hoàn thành ----------

    @Test
    @DisplayName("Bác sĩ cập nhật nội dung khám và tóm tắt bệnh án; audit chỉ ghi tên trường, không ghi nội dung")
    void updateEncounterAndClinicalSummary() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("enc_update");
        IsolatedPatient pat = data.isolatedPatient("enc_update");
        Appointment appointment = data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.BOOKED);
        long encounterId = startExamOk(doc.token(), appointment.getId());

        update(doc.token(), encounterId, """
                {"diagnosis":"Viêm họng cấp","clinicalNotes":"BN tỉnh, họng đỏ","treatmentPlan":"Nghỉ ngơi",
                 "followUpNote":"Tái khám sau 1 tuần",
                 "clinicalSummary":{"bloodType":"ab+","allergyNotes":"Dị ứng penicillin","currentMedications":"Paracetamol"}}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Viêm họng cấp"))
                .andExpect(jsonPath("$.followUpNote").value("Tái khám sau 1 tuần"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.editable").value(true));

        MedicalRecord record = medicalRecords.getOrCreateByPatientId(pat.patientId());
        assertEquals("ab+", record.getBloodType());
        assertEquals("Dị ứng penicillin", record.getAllergyNotes());
        assertEquals("Paracetamol", record.getCurrentMedications());

        var audits = auditsOf(AuditActions.ENTITY_ENCOUNTERS, encounterId).stream()
                .filter(a -> a.getActionCode().equals(AuditActions.ENCOUNTER_UPDATE)).toList();
        assertEquals(1, audits.size());
        String metadata = audits.getFirst().getMetadataJson();
        assertTrue(metadata.contains("diagnosis"), metadata);
        assertFalse(metadata.contains("Viêm họng"), "audit không được chứa nội dung lâm sàng: " + metadata);
    }

    @Test
    @DisplayName("Chỉ bác sĩ phụ trách sửa được: bác sĩ khác, bệnh nhân, Admin → 403")
    void onlyOwningDoctorUpdates() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("enc_upd_owner");
        IsolatedDoctor other = data.isolatedDoctor("enc_upd_other");
        IsolatedPatient pat = data.isolatedPatient("enc_upd_pat");
        long encounterId = startExamOk(doc.token(),
                data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.BOOKED).getId());

        String body = "{\"diagnosis\":\"Chẩn đoán giả mạo\"}";
        update(other.token(), encounterId, body).andExpect(status().isForbidden());
        update(pat.token(), encounterId, body).andExpect(status().isForbidden());
        update(adminToken(), encounterId, body).andExpect(status().isForbidden());
        update(doc.token(), 999_999L, body).andExpect(status().isNotFound());

        read(doc.token(), encounterId).andExpect(jsonPath("$.diagnosis").doesNotExist());
        assertEquals(1, countAudits(AuditActions.ENTITY_ENCOUNTERS, encounterId, AuditActions.ACCESS_DENIED,
                other.userId()));
    }

    @Test
    @DisplayName("Hoàn thành khám: lịch → COMPLETED cùng lúc, sau đó mọi chỉnh sửa → 409")
    void completeEncounter() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("enc_complete");
        IsolatedPatient pat = data.isolatedPatient("enc_complete");
        Appointment appointment = data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.BOOKED);
        long encounterId = startExamOk(doc.token(), appointment.getId());

        // chưa có chẩn đoán → không cho hoàn thành, lịch vẫn IN_PROGRESS
        update(doc.token(), encounterId, "{\"status\":\"COMPLETED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.diagnosis").exists());
        assertEquals(AppointmentStatus.IN_PROGRESS, statusOf(appointment.getId()));

        update(doc.token(), encounterId, "{\"diagnosis\":\"Cảm cúm\",\"status\":\"COMPLETED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.editable").value(false));
        assertEquals(AppointmentStatus.COMPLETED, statusOf(appointment.getId()));
        assertEquals(1, countAudits(AuditActions.ENTITY_ENCOUNTERS, encounterId, AuditActions.ENCOUNTER_COMPLETE,
                doc.userId()));

        update(doc.token(), encounterId, "{\"diagnosis\":\"Sửa sau khi hoàn thành\"}")
                .andExpect(status().isConflict());
        update(doc.token(), encounterId, "{\"status\":\"COMPLETED\"}").andExpect(status().isConflict());
        read(doc.token(), encounterId)
                .andExpect(jsonPath("$.diagnosis").value("Cảm cúm"))
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    @DisplayName("Trạng thái không hợp lệ hoặc nhóm máu sai định dạng → 400 kèm details")
    void updateValidation() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("enc_valid");
        IsolatedPatient pat = data.isolatedPatient("enc_valid");
        long encounterId = startExamOk(doc.token(),
                data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.BOOKED).getId());

        update(doc.token(), encounterId, "{\"status\":\"CANCELLED\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.status").exists());
        update(doc.token(), encounterId, "{\"clinicalSummary\":{\"bloodType\":\"Z9\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details['clinicalSummary.bloodType']").exists());
        update(doc.token(), encounterId, "{\"diagnosis\":\"" + "x".repeat(5001) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.diagnosis").exists());
    }

    // ---------- đọc ----------

    @Test
    @DisplayName("Đọc chi tiết: bệnh nhân chủ và bác sĩ phụ trách 200 + audit ENCOUNTER_VIEW; người khác, Admin → 403")
    void readAccessAndAudit() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("enc_read_doc");
        IsolatedDoctor other = data.isolatedDoctor("enc_read_other");
        IsolatedPatient owner = data.isolatedPatient("enc_read_owner");
        IsolatedPatient stranger = data.isolatedPatient("enc_read_stranger");
        long encounterId = startExamOk(doc.token(),
                data.newAppointment(doc.doctorId(), owner.patientId(), AppointmentStatus.BOOKED).getId());

        read(owner.token(), encounterId).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(encounterId))
                .andExpect(jsonPath("$.editable").value(false));
        read(doc.token(), encounterId).andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(true));
        assertEquals(1, countAudits(AuditActions.ENTITY_ENCOUNTERS, encounterId, AuditActions.ENCOUNTER_VIEW,
                owner.userId()));
        assertEquals(1, countAudits(AuditActions.ENTITY_ENCOUNTERS, encounterId, AuditActions.ENCOUNTER_VIEW,
                doc.userId()));

        read(stranger.token(), encounterId).andExpect(status().isForbidden());
        read(other.token(), encounterId).andExpect(status().isForbidden());
        read(adminToken(), encounterId).andExpect(status().isForbidden());
        read(owner.token(), 999_999L).andExpect(status().isNotFound());

        assertEquals(1, countAudits(AuditActions.ENTITY_ENCOUNTERS, encounterId, AuditActions.ACCESS_DENIED,
                stranger.userId()));
        assertEquals(1, countAudits(AuditActions.ENTITY_ENCOUNTERS, encounterId, AuditActions.ACCESS_DENIED,
                ADMIN_USER_ID));
        assertEquals(0, countAudits(AuditActions.ENTITY_ENCOUNTERS, encounterId, AuditActions.ENCOUNTER_VIEW,
                stranger.userId()));
    }

    @Test
    @DisplayName("GET /encounters/me chỉ trả lịch sử của chính bệnh nhân, mới nhất trước, có phân trang")
    void myHistoryIsOwnOnly() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("enc_hist");
        IsolatedPatient mine = data.isolatedPatient("enc_hist_mine");
        IsolatedPatient theirs = data.isolatedPatient("enc_hist_theirs");
        long first = startExamOk(doc.token(),
                data.newAppointment(doc.doctorId(), mine.patientId(), AppointmentStatus.BOOKED).getId());
        long second = startExamOk(doc.token(),
                data.newAppointment(doc.doctorId(), mine.patientId(), AppointmentStatus.BOOKED).getId());
        long notMine = startExamOk(doc.token(),
                data.newAppointment(doc.doctorId(), theirs.patientId(), AppointmentStatus.BOOKED).getId());

        MvcResult result = mvc.perform(get("/api/v1/encounters/me").header("Authorization", mine.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].doctorName").value("Bác sĩ thử nghiệm enc_hist"))
                .andExpect(jsonPath("$.content[0].patientName").value("Bệnh nhân thử nghiệm enc_hist_mine"))
                .andReturn();
        JsonNode content = body(result).get("content");
        assertEquals(second, content.get(0).get("id").asLong(), "mới nhất trước");
        assertEquals(first, content.get(1).get("id").asLong());
        assertFalse(content.toString().contains("\"id\":" + notMine + ","));

        mvc.perform(get("/api/v1/encounters/me?size=1&page=1").header("Authorization", mine.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
        // bệnh nhân chưa từng khám: trang rỗng, không lỗi
        mvc.perform(get("/api/v1/encounters/me").header("Authorization",
                data.isolatedPatient("enc_hist_empty").token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertTrue(auditLogs.findAll().stream().anyMatch(a -> AuditActions.ENCOUNTER_VIEW.equals(a.getActionCode())
                && a.getActorUser() != null && a.getActorUser().getId().equals(mine.userId())));
    }

    @Test
    @DisplayName("Bác sĩ tra cứu theo lịch/bệnh án: chỉ thấy lần khám của mình trong phạm vi phụ trách")
    void doctorLookupIsScoped() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("enc_look_a");
        IsolatedDoctor other = data.isolatedDoctor("enc_look_b");
        IsolatedPatient pat = data.isolatedPatient("enc_look");
        Appointment appointment = data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.BOOKED);
        long encounterId = startExamOk(doc.token(), appointment.getId());
        long recordId = medicalRecords.getOrCreateByPatientId(pat.patientId()).getId();

        mvc.perform(get("/api/v1/encounters?appointmentId=" + appointment.getId())
                .header("Authorization", doc.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(encounterId));
        mvc.perform(get("/api/v1/encounters?medicalRecordId=" + recordId).header("Authorization", doc.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));

        // bác sĩ khác: lịch không thuộc mình → 403; bệnh án ngoài phạm vi → 403
        mvc.perform(get("/api/v1/encounters?appointmentId=" + appointment.getId())
                .header("Authorization", other.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/encounters?medicalRecordId=" + recordId).header("Authorization", other.token()))
                .andExpect(status().isForbidden());
        // lịch chưa có lần khám: trang rỗng
        Appointment fresh = data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.BOOKED);
        mvc.perform(get("/api/v1/encounters?appointmentId=" + fresh.getId()).header("Authorization", doc.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertNotNull(auditsOf(AuditActions.ENTITY_MEDICAL_RECORDS, recordId));
    }
}
