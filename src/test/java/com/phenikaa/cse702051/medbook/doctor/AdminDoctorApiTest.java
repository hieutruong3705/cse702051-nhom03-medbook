package com.phenikaa.cse702051.medbook.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.dto.doctor.DoctorProfileData;
import com.phenikaa.cse702051.medbook.exception.FieldConflictException;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Specialty;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.SpecialtyRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.service.DoctorService;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-02.3, 02.4, 02.6: Admin quản lý hồ sơ bác sĩ. Tạo hồ sơ chỉ cho tài khoản có vai trò bác sĩ và chưa có hồ sơ;
 * xóa bác sĩ đã có lịch sử chỉ ngừng hoạt động và gỡ giờ trống tương lai, không đụng lịch đã đặt.
 */
class AdminDoctorApiTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "/api/v1/admin/doctors";

    @Autowired
    private DoctorRepository doctors;
    @Autowired
    private UserRepository users;
    @Autowired
    private SpecialtyRepository specialties;
    @Autowired
    private AppointmentSlotRepository slots;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private DoctorService doctorService;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private User doctorAccount(String token) {
        return data.user("admdoc_" + token.toLowerCase(), "Iso#Pass123", "DOCTOR");
    }

    private static String body(Long userId, String fullName, Object specialtyId, String license) {
        return "{\"userId\":%s,\"fullName\":\"%s\",\"specialtyId\":%s,\"phone\":\"0988 111 222\","
                .formatted(userId, fullName, specialtyId)
                + "\"licenseNumber\":\"%s\",\"bio\":\"Bác sĩ nội trú\"}".formatted(license);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long create(User account, String token) throws Exception {
        MvcResult result = send(post(URL), body(account.getId(), "Bác sĩ " + token, 1, "GP-" + token))
                .andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ---------- tạo ----------

    @Test
    @DisplayName("AC-02.3 Tạo hồ sơ cho tài khoản có vai trò bác sĩ: 201, đủ trường quản trị, ghi một bản audit")
    void createsDoctorProfile() throws Exception {
        String token = unique();
        User account = doctorAccount(token);

        MvcResult result = send(post(URL), body(account.getId(), "  Bác sĩ " + token + " ", 1, " GP-" + token + " "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(account.getId()))
                .andExpect(jsonPath("$.username").value(account.getUsername()))
                .andExpect(jsonPath("$.fullName").value("Bác sĩ " + token))
                .andExpect(jsonPath("$.specialtyId").value(1))
                .andExpect(jsonPath("$.specialtyName").value("Nội tổng quát"))
                .andExpect(jsonPath("$.phone").value("0988 111 222"))
                .andExpect(jsonPath("$.licenseNumber").value("GP-" + token))
                .andExpect(jsonPath("$.bio").value("Bác sĩ nội trú"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.hasHistory").value(false))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();
        long id = JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        assertEquals(account.getId(), doctors.findById(id).orElseThrow().getUserId());
        assertEquals(1, countAudits(AuditActions.ENTITY_DOCTORS, id, AuditActions.DOCTOR_CREATE, ADMIN_USER_ID));
        // hồ sơ mới xuất hiện ngay ở trang công khai và trang quản trị
        mvc.perform(get("/api/v1/doctors/" + id)).andExpect(status().isOk());
        mvc.perform(get(URL + "/" + id).header("Authorization", adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.licenseNumber").value("GP-" + token));
    }

    @Test
    @DisplayName("AC-02.3 Tạo hồ sơ: tài khoản không có vai trò bác sĩ hoặc không tồn tại → 400 userId; đã có hồ sơ → 409")
    void rejectsWrongOrDuplicateAccount() throws Exception {
        String token = unique();
        IsolatedPatient patient = data.isolatedPatient("admdoc_pat");
        User account = doctorAccount(token);

        send(post(URL), body(patient.userId(), "Không phải bác sĩ", 1, "NP-" + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.userId").value("Tài khoản chưa có vai trò bác sĩ"));
        send(post(URL), body(999_999L, "Không tồn tại", 1, "NX-" + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.userId").value("Tài khoản không tồn tại"));
        send(post(URL), body(null, "Thiếu tài khoản", 1, "NN-" + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.userId").exists());

        create(account, token);
        send(post(URL), body(account.getId(), "Hồ sơ thứ hai", 1, "G2-" + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.userId").value("Tài khoản này đã có hồ sơ bác sĩ"));
        assertFalse(doctors.existsByLicenseNumberIgnoreCase("G2-" + token));
        assertFalse(doctors.existsByLicenseNumberIgnoreCase("NP-" + token));
    }

    @Test
    @DisplayName("AC-02.3 Tạo hồ sơ: chuyên khoa INACTIVE hoặc không tồn tại → 400 specialtyId; trùng số giấy phép → 409")
    void rejectsInactiveSpecialtyAndDuplicateLicense() throws Exception {
        String token = unique();
        Specialty closed = specialties.save(Specialty.builder().code("ADI" + token).name("Đã đóng " + token)
                .status("INACTIVE").build());
        User first = doctorAccount(token + "a");
        User second = doctorAccount(token + "b");

        send(post(URL), body(first.getId(), "Bác sĩ", closed.getId(), "IS-" + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.specialtyId").value("Chuyên khoa không tồn tại hoặc đã ngừng sử dụng"));
        send(post(URL), body(first.getId(), "Bác sĩ", 999_999, "IX-" + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.specialtyId").exists());
        assertTrue(doctors.findByUserId(first.getId()).isEmpty(), "yêu cầu sai không được để lại hồ sơ");

        send(post(URL), body(first.getId(), "Bác sĩ A", 1, "DUP-" + token)).andExpect(status().isCreated());
        send(post(URL), body(second.getId(), "Bác sĩ B", 1, "dup-" + token.toLowerCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.licenseNumber").value("Số giấy phép hành nghề đã thuộc về hồ sơ khác"));
        assertTrue(doctors.findByUserId(second.getId()).isEmpty());
    }

    @Test
    @DisplayName("Tạo hồ sơ: thiếu họ tên, số giấy phép, chuyên khoa hoặc điện thoại sai → 400 đúng trường")
    void validatesRequiredFields() throws Exception {
        User account = doctorAccount(unique());
        send(post(URL), "{\"userId\":%d}".formatted(account.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fullName").value("Họ và tên không được để trống"))
                .andExpect(jsonPath("$.details.licenseNumber").value("Số giấy phép hành nghề không được để trống"))
                .andExpect(jsonPath("$.details.specialtyId").value("Phải chọn chuyên khoa"));
        send(post(URL), "{\"userId\":%d,\"fullName\":\"A\",\"specialtyId\":1,\"licenseNumber\":\"X\",\"phone\":\"abc\"}"
                .formatted(account.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.phone").value("Số điện thoại không hợp lệ"));
        assertTrue(doctors.findByUserId(account.getId()).isEmpty());
    }

    // ---------- sửa, danh sách ----------

    @Test
    @DisplayName("Sửa hồ sơ: đổi tên, chuyên khoa, số giấy phép; trùng số giấy phép của người khác → 409; không tồn tại → 404")
    void updatesDoctorProfile() throws Exception {
        String token = unique();
        long id = create(doctorAccount(token + "a"), token + "A");
        long other = create(doctorAccount(token + "b"), token + "B");
        User unrelated = doctorAccount(token + "c");

        // userId trong body bị bỏ qua: hồ sơ không đổi được sang tài khoản khác
        send(put(URL + "/" + id), body(unrelated.getId(), "Tên mới " + token, 2, "NEW-" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Tên mới " + token))
                .andExpect(jsonPath("$.specialtyId").value(2))
                .andExpect(jsonPath("$.specialtyName").value("Nhi khoa"))
                .andExpect(jsonPath("$.licenseNumber").value("NEW-" + token));
        assertFalse(doctors.findById(id).orElseThrow().getUserId().equals(unrelated.getId()));
        assertEquals(1, countAudits(AuditActions.ENTITY_DOCTORS, id, AuditActions.DOCTOR_UPDATE, ADMIN_USER_ID));

        // giữ nguyên số giấy phép của chính mình thì được
        send(put(URL + "/" + id), body(null, "Tên mới " + token, 2, "NEW-" + token)).andExpect(status().isOk());
        send(put(URL + "/" + id), body(null, "Tên mới", 2, "GP-" + token + "B"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.licenseNumber").exists());
        assertEquals("GP-" + token + "B", doctors.findById(other).orElseThrow().getLicenseNumber());
        send(put(URL + "/999999"), body(null, "Không có", 1, "ZZ-" + token)).andExpect(status().isNotFound());
        mvc.perform(get(URL + "/999999").header("Authorization", adminToken())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Danh sách quản trị: tìm theo tên hoặc số giấy phép, lọc isActive, kèm username và hasHistory")
    void listsDoctorsForAdmin() throws Exception {
        String token = unique();
        User account = doctorAccount(token + "a");
        long fresh = create(account, token + "A");
        IsolatedDoctor experienced = data.isolatedDoctor("admdoc_hist_" + token);
        IsolatedPatient patient = data.isolatedPatient("admdoc_hist_pat");
        data.newAppointment(experienced.doctorId(), patient.patientId(), AppointmentStatus.COMPLETED);

        mvc.perform(get(URL).param("keyword", "gp-" + token.toLowerCase() + "a").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(fresh))
                .andExpect(jsonPath("$.content[0].username").value(account.getUsername()))
                .andExpect(jsonPath("$.content[0].hasHistory").value(false))
                .andExpect(jsonPath("$.content[0].isActive").value(true));
        mvc.perform(get(URL).param("keyword", "admdoc_hist_" + token).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(experienced.doctorId()))
                .andExpect(jsonPath("$.content[0].hasHistory").value(true));

        // ngừng hoạt động một hồ sơ rồi lọc theo isActive
        Doctor profile = doctors.findById(fresh).orElseThrow();
        profile.setIsActive(false);
        doctors.save(profile);
        mvc.perform(get(URL).param("keyword", token + "A").param("isActive", "false")
                .header("Authorization", adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(URL).param("keyword", token + "A").param("isActive", "true")
                .header("Authorization", adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(URL).param("size", "500").header("Authorization", adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    // ---------- xóa ----------

    @Test
    @DisplayName("AC-02.4 Xóa bác sĩ có lịch sử: 200, isActive=false, giờ trống tương lai bị gỡ, lịch đã đặt giữ nguyên")
    void deletingDoctorWithHistoryOnlyDeactivates() throws Exception {
        String token = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("admdoc_soft_" + token);
        IsolatedPatient patient = data.isolatedPatient("admdoc_soft_pat");
        LocalDate day = LocalDate.now().plusDays(1500);
        AppointmentSlot neverUsed = data.slot(doctor.doctorId(), day, LocalTime.of(8, 0));
        AppointmentSlot onceCancelled = data.slot(doctor.doctorId(), day, LocalTime.of(9, 0));
        AppointmentSlot past = data.slot(doctor.doctorId(), LocalDate.now().minusDays(3), LocalTime.of(8, 0));
        Appointment booked = data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.BOOKED);
        // một lịch đã hủy trên slot 09:00: slot này có lịch sử nên không xóa cứng được
        Appointment cancelled = Appointment.builder().patientId(patient.patientId())
                .doctor(doctors.findById(doctor.doctorId()).orElseThrow()).slot(onceCancelled)
                .status(AppointmentStatus.BOOKED).build();
        cancelled = appointments.save(cancelled);
        cancelled.cancel("test", LocalDateTime.now());
        appointments.save(cancelled);

        mvc.perform(delete(URL + "/" + doctor.doctorId()).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doctor.doctorId()))
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.hasHistory").value(true));

        assertFalse(doctors.findById(doctor.doctorId()).orElseThrow().getIsActive());
        assertTrue(slots.findById(neverUsed.getId()).isEmpty(), "giờ trống chưa từng có lịch hẹn được xóa hẳn");
        AppointmentSlot kept = slots.findById(onceCancelled.getId()).orElseThrow();
        assertEquals("CANCELLED", kept.getStatus(), "giờ trống đã có lịch sử được gỡ khỏi lịch, không xóa cứng");
        assertFalse(kept.getIsAvailable());
        assertTrue(slots.findById(past.getId()).isPresent(), "slot đã qua không bị đụng tới");
        // lịch đã đặt và slot của nó còn nguyên
        Appointment stillBooked = appointments.findById(booked.getId()).orElseThrow();
        assertEquals(AppointmentStatus.BOOKED, stillBooked.getStatus());
        assertEquals("BOOKED", slots.findById(booked.getSlot().getId()).orElseThrow().getStatus());
        assertEquals(1, countAudits(AuditActions.ENTITY_DOCTORS, doctor.doctorId(), AuditActions.DOCTOR_DELETE,
                ADMIN_USER_ID));

        // bác sĩ biến mất khỏi trang công khai và không còn giờ trống để đặt
        mvc.perform(get("/api/v1/doctors/" + doctor.doctorId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/doctors/%d/slots".formatted(doctor.doctorId())).param("date", day.toString()))
                .andExpect(status().isNotFound());
        // xóa lần nữa vẫn 200 (đã ngừng hoạt động), không lỗi
        mvc.perform(delete(URL + "/" + doctor.doctorId()).header("Authorization", adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @DisplayName("AC-02.4 Xóa bác sĩ chưa có lịch sử: 204, hồ sơ và giờ trống biến mất, tài khoản còn nguyên")
    void deletingDoctorWithoutHistoryRemovesProfile() throws Exception {
        String token = unique();
        User account = doctorAccount(token);
        long id = create(account, token);
        AppointmentSlot slot = data.slot(id, LocalDate.now().plusDays(1501), LocalTime.of(8, 0));

        mvc.perform(delete(URL + "/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        assertTrue(doctors.findById(id).isEmpty());
        assertTrue(slots.findById(slot.getId()).isEmpty());
        assertTrue(users.findById(account.getId()).isPresent(), "xóa hồ sơ bác sĩ không xóa tài khoản");
        mvc.perform(get(URL + "/" + id).header("Authorization", adminToken())).andExpect(status().isNotFound());
        mvc.perform(delete(URL + "/" + id).header("Authorization", adminToken())).andExpect(status().isNotFound());
        // tài khoản lại có thể được tạo hồ sơ mới
        send(post(URL), body(account.getId(), "Tạo lại", 1, "RE-" + token)).andExpect(status().isCreated());
    }

    // ---------- quyền ----------

    @Test
    @DisplayName("AC-02.6 Quyền: khách 401; bệnh nhân và bác sĩ 403 ở mọi thao tác quản trị hồ sơ bác sĩ")
    void onlyAdminManagesDoctors() throws Exception {
        String payload = body(2L, "Không được", 1, "NO-" + unique());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        for (String token : new String[] { patientToken(), doctorToken() }) {
            mvc.perform(get(URL).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get(URL + "/1").header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content(payload)).andExpect(status().isForbidden());
            mvc.perform(put(URL + "/1").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content(payload)).andExpect(status().isForbidden());
            mvc.perform(delete(URL + "/1").header("Authorization", token)).andExpect(status().isForbidden());
        }
        assertTrue(doctors.findById(1L).orElseThrow().getIsActive(), "bác sĩ seed không bị ảnh hưởng");
    }

    // ---------- hàm nội bộ cho module tài khoản ----------

    @Test
    @DisplayName("createProfileForUser dùng họ tên tài khoản khi không truyền; syncContact đồng bộ tên và điện thoại")
    void internalProfileFunctions() {
        String token = unique();
        User account = doctorAccount(token);

        Doctor created = doctorService.createProfileForUser(account.getId(),
                new DoctorProfileData(null, 1L, "0911222333", "IN-" + token, null));
        assertEquals(account.getFullName(), created.getFullName());
        assertEquals("0911222333", created.getPhone());
        assertTrue(created.getIsActive());
        org.junit.jupiter.api.Assertions.assertThrows(FieldConflictException.class,
                () -> doctorService.createProfileForUser(account.getId(),
                        new DoctorProfileData("Lần hai", 1L, null, "IN2-" + token, null)));

        doctorService.syncContact(account.getId(), "  Họ tên mới  ", "0900111222");
        Doctor synced = doctors.findById(created.getId()).orElseThrow();
        assertEquals("Họ tên mới", synced.getFullName());
        assertEquals("0900111222", synced.getPhone());
        // giá trị rỗng giữ nguyên; tài khoản không có hồ sơ bác sĩ thì không làm gì và không lỗi
        doctorService.syncContact(account.getId(), " ", null);
        assertEquals("Họ tên mới", doctors.findById(created.getId()).orElseThrow().getFullName());
        doctorService.syncContact(PATIENT1_USER_ID, "Không liên quan", "0900000000");
    }
}
