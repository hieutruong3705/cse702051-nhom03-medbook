package com.phenikaa.cse702051.medbook.review;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.NotificationRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * Đánh giá lần khám (YCCN-38 đến 41, 44; QTNV-16 đến 18): chỉ chủ lịch hẹn đã COMPLETED mới đánh giá được, mỗi lịch
 * một lần; trang công khai chỉ thấy đánh giá đang hiện với tên rút gọn; quản trị viên ẩn kèm lý do và mọi thay đổi
 * đều có nhật ký.
 */
class ReviewApiTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private PatientRepository patients;
    @Autowired
    private NotificationRepository notifications;

    private static String key() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String body(int rating, String comment) throws Exception {
        return JSON.writeValueAsString(java.util.Map.of("rating", rating, "comment", comment));
    }

    private Long review(String token, Long appointmentId, int rating, String comment) throws Exception {
        String json = mvc.perform(post("/api/v1/appointments/{id}/review", appointmentId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(rating, comment)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(json).get("id").asLong();
    }

    private void named(IsolatedPatient patient, String fullName) {
        Patient entity = patients.findById(patient.patientId()).orElseThrow();
        entity.setFullName(fullName);
        patients.save(entity);
    }

    @Test
    @DisplayName("Bệnh nhân đánh giá lần khám đã hoàn thành của mình: 201, Location, bác sĩ lấy từ lịch hẹn, có nhật ký và thông báo")
    void patientReviewsOwnCompletedVisit() throws Exception {
        String k = key();
        IsolatedDoctor doctor = data.isolatedDoctor("rv1" + k);
        IsolatedPatient patient = data.isolatedPatient("rv1" + k);
        Appointment visit = data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.COMPLETED);

        String json = mvc.perform(post("/api/v1/appointments/{id}/review", visit.getId())
                .header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(5, "  Bác sĩ tận tình  ")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/reviews/")))
                .andExpect(jsonPath("$.doctorId").value(doctor.doctorId()))
                .andExpect(jsonPath("$.appointmentId").value(visit.getId()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Bác sĩ tận tình"))
                .andExpect(jsonPath("$.status").value("VISIBLE"))
                .andReturn().getResponse().getContentAsString();
        Long reviewId = JSON.readTree(json).get("id").asLong();

        assertEquals(1, countAudits(AuditActions.ENTITY_DOCTOR_REVIEWS, reviewId, AuditActions.REVIEW_CREATE,
                patient.userId()));
        assertTrue(notifications.findAll().stream().anyMatch(n -> n.getUser().getId().equals(doctor.userId())
                && "REVIEW_RECEIVED".equals(n.getType())), "bác sĩ nhận thông báo có đánh giá mới");

        mvc.perform(get("/api/v1/appointments/{id}/review", visit.getId()).header("Authorization", patient.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reviewId));
    }

    @Test
    @DisplayName("Lịch chưa hoàn thành → 422; đánh giá lần thứ hai cùng lịch → 409; số sao 0 hoặc 6 → 400")
    void rejectsUnfinishedDuplicateAndOutOfRange() throws Exception {
        String k = key();
        IsolatedDoctor doctor = data.isolatedDoctor("rv2" + k);
        IsolatedPatient patient = data.isolatedPatient("rv2" + k);
        Appointment booked = data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.BOOKED);
        Appointment inProgress = data.newAppointment(doctor.doctorId(), patient.patientId(),
                AppointmentStatus.IN_PROGRESS);
        Appointment done = data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.COMPLETED);

        for (Appointment unfinished : new Appointment[] { booked, inProgress }) {
            mvc.perform(post("/api/v1/appointments/{id}/review", unfinished.getId())
                    .header("Authorization", patient.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(4, "Chưa khám")))
                    .andExpect(status().isUnprocessableContent());
        }
        for (int stars : new int[] { 0, 6 }) {
            mvc.perform(post("/api/v1/appointments/{id}/review", done.getId())
                    .header("Authorization", patient.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(stars, "Sai số sao")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.rating").exists());
        }
        review(patient.token(), done.getId(), 4, "Lần đầu");
        mvc.perform(post("/api/v1/appointments/{id}/review", done.getId())
                .header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(1, "Lần hai")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/appointments/{id}/review", 999_999_999L)
                .header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(5, "Không tồn tại")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("IDOR: đánh giá lịch hẹn của bệnh nhân khác → 403 kèm ACCESS_DENIED; sửa, xóa đánh giá của người khác → 404")
    void cannotReviewOrChangeAnotherPatientsVisit() throws Exception {
        String k = key();
        IsolatedDoctor doctor = data.isolatedDoctor("rv3" + k);
        IsolatedPatient owner = data.isolatedPatient("rv3a" + k);
        IsolatedPatient other = data.isolatedPatient("rv3b" + k);
        Appointment visit = data.newAppointment(doctor.doctorId(), owner.patientId(), AppointmentStatus.COMPLETED);

        mvc.perform(post("/api/v1/appointments/{id}/review", visit.getId())
                .header("Authorization", other.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(1, "Đánh giá hộ")))
                .andExpect(status().isForbidden());
        assertEquals(1, countAudits(AuditActions.ENTITY_APPOINTMENTS, visit.getId(), AuditActions.ACCESS_DENIED,
                other.userId()));

        Long reviewId = review(owner.token(), visit.getId(), 5, "Của chủ");
        mvc.perform(put("/api/v1/reviews/{id}", reviewId)
                .header("Authorization", other.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(1, "Sửa trộm")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/reviews/{id}", reviewId).header("Authorization", other.token()))
                .andExpect(status().isNotFound());
        assertEquals(2, countAudits(AuditActions.ENTITY_DOCTOR_REVIEWS, reviewId, AuditActions.ACCESS_DENIED,
                other.userId()));
        mvc.perform(get("/api/v1/appointments/{id}/review", visit.getId()).header("Authorization", other.token()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/reviews/me").header("Authorization", owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.content[0].comment").value("Của chủ"));
    }

    @Test
    @DisplayName("Phá vỡ kiểm soát truy cập: khách 401; bác sĩ, lễ tân, quản trị viên gửi đánh giá hoặc xem /reviews/me → 403")
    void onlyPatientsCanWriteReviews() throws Exception {
        String k = key();
        IsolatedDoctor doctor = data.isolatedDoctor("rv4" + k);
        IsolatedPatient patient = data.isolatedPatient("rv4" + k);
        Appointment visit = data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.COMPLETED);

        mvc.perform(post("/api/v1/appointments/{id}/review", visit.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(5, "Khách")))
                .andExpect(status().isUnauthorized());
        for (String token : new String[] { doctor.token(), receptionistToken(), adminToken() }) {
            mvc.perform(post("/api/v1/appointments/{id}/review", visit.getId())
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(5, "Không phải bệnh nhân")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/reviews/me").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/admin/reviews").header("Authorization", patient.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Công khai: tên người đánh giá rút gọn, không lộ mã bệnh nhân hay lịch hẹn; nội dung <script> trả về nguyên văn dạng chuỗi")
    void publicReviewsHideIdentityAndKeepTextAsText() throws Exception {
        String k = key();
        IsolatedDoctor doctor = data.isolatedDoctor("rv5" + k);
        IsolatedPatient patient = data.isolatedPatient("rv5" + k);
        named(patient, "Nguyễn Văn An");
        Appointment visit = data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.COMPLETED);
        String payload = "<script>alert('xss')</script> Rất tốt";
        review(patient.token(), visit.getId(), 5, payload);

        String json = mvc.perform(get("/api/v1/doctors/{id}/reviews", doctor.doctorId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reviewerName").value("Nguyễn V. A."))
                .andExpect(jsonPath("$.content[0].comment").value(payload))
                .andReturn().getResponse().getContentAsString();
        JsonNode first = JSON.readTree(json).get("content").get(0);
        for (String hidden : new String[] { "patientId", "appointmentId", "patientCode", "patientName", "status",
                "hiddenReason" }) {
            assertFalse(first.has(hidden), "trang công khai không được có " + hidden);
        }
        assertFalse(json.contains("Nguyễn Văn An"), "không lộ họ tên đầy đủ");
        mvc.perform(get("/api/v1/reviews").param("doctorId", String.valueOf(doctor.doctorId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Quản trị viên ẩn kèm lý do: đánh giá biến khỏi trang công khai và điểm trung bình; hiện lại thì tính lại; có nhật ký")
    void adminHideRemovesReviewFromPublicAndAverage() throws Exception {
        String k = key();
        IsolatedDoctor doctor = data.isolatedDoctor("rv6" + k);
        IsolatedPatient patient = data.isolatedPatient("rv6" + k);
        Long five = review(patient.token(), data.newAppointment(doctor.doctorId(), patient.patientId(),
                AppointmentStatus.COMPLETED).getId(), 5, "Năm sao");
        Long one = review(patient.token(), data.newAppointment(doctor.doctorId(), patient.patientId(),
                AppointmentStatus.COMPLETED).getId(), 1, "Nội dung vi phạm");

        mvc.perform(get("/api/v1/doctors/{id}/rating-summary", doctor.doctorId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(3.0))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.distribution[0]").value(1))
                .andExpect(jsonPath("$.distribution[4]").value(1));

        mvc.perform(patch("/api/v1/admin/reviews/{id}/hide", one)
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/reviews/{id}/hide", one)
                .header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ngôn từ không phù hợp\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.hiddenReason").value("Ngôn từ không phù hợp"));

        mvc.perform(get("/api/v1/doctors/{id}/reviews", doctor.doctorId()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(one.intValue()))));
        mvc.perform(get("/api/v1/doctors/{id}/rating-summary", doctor.doctorId()))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
        mvc.perform(get("/api/v1/doctors/{id}", doctor.doctorId()))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
        mvc.perform(put("/api/v1/reviews/{id}", one)
                .header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(2, "Sửa sau khi bị ẩn")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/reviews/me").header("Authorization", patient.token()))
                .andExpect(jsonPath("$.content[?(@.id == " + one + ")].status").value("HIDDEN"));

        mvc.perform(patch("/api/v1/admin/reviews/{id}/show", one).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VISIBLE"));
        mvc.perform(get("/api/v1/doctors/{id}/rating-summary", doctor.doctorId()))
                .andExpect(jsonPath("$.averageRating").value(3.0));

        assertEquals(1, countAudits(AuditActions.ENTITY_DOCTOR_REVIEWS, one, AuditActions.REVIEW_HIDE,
                ADMIN_USER_ID));
        assertEquals(1, countAudits(AuditActions.ENTITY_DOCTOR_REVIEWS, one, AuditActions.REVIEW_SHOW,
                ADMIN_USER_ID));
        mvc.perform(get("/api/v1/admin/reviews").param("doctorId", String.valueOf(doctor.doctorId()))
                .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].patientCode").exists());
        assertTrue(five > 0);
    }

    @Test
    @DisplayName("Bệnh nhân sửa và xóa đánh giá của mình; bác sĩ xem được đánh giá về mình, không thấy của bác sĩ khác")
    void ownerEditsAndDeletesDoctorSeesOwnReviews() throws Exception {
        String k = key();
        IsolatedDoctor doctor = data.isolatedDoctor("rv7" + k);
        IsolatedDoctor otherDoctor = data.isolatedDoctor("rv7o" + k);
        IsolatedPatient patient = data.isolatedPatient("rv7" + k);
        Long reviewId = review(patient.token(), data.newAppointment(doctor.doctorId(), patient.patientId(),
                AppointmentStatus.COMPLETED).getId(), 3, "Tạm được");
        review(patient.token(), data.newAppointment(otherDoctor.doctorId(), patient.patientId(),
                AppointmentStatus.COMPLETED).getId(), 2, "Bác sĩ khác");

        mvc.perform(put("/api/v1/reviews/{id}", reviewId)
                .header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(4, "Đã sửa")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.comment").value("Đã sửa"));
        assertEquals(1, countAudits(AuditActions.ENTITY_DOCTOR_REVIEWS, reviewId, AuditActions.REVIEW_UPDATE,
                patient.userId()));

        mvc.perform(get("/api/v1/doctors/me/reviews").header("Authorization", doctor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].comment").value("Đã sửa"));
        mvc.perform(get("/api/v1/doctors/me/rating-summary").header("Authorization", doctor.token()))
                .andExpect(jsonPath("$.averageRating").value(4.0));
        mvc.perform(get("/api/v1/doctors/me/reviews").header("Authorization", patient.token()))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/reviews/{id}", reviewId).header("Authorization", patient.token()))
                .andExpect(status().isNoContent());
        assertEquals(1, countAudits(AuditActions.ENTITY_DOCTOR_REVIEWS, reviewId, AuditActions.REVIEW_DELETE,
                patient.userId()));
        mvc.perform(get("/api/v1/doctors/{id}/reviews", doctor.doctorId()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Lọc công khai: số sao ngoài 1-5 → 400; cột sắp xếp ngoài danh sách trắng → 400; bác sĩ không tồn tại → 404")
    void publicFiltersAreValidated() throws Exception {
        mvc.perform(get("/api/v1/reviews").param("rating", "9")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reviews").param("sort", "patient.fullName")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/doctors/{id}/reviews", 999_999_999L)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/doctors/{id}/rating-summary", 999_999_999L)).andExpect(status().isNotFound());
    }
}
