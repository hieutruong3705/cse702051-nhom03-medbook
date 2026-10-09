package com.phenikaa.cse702051.medbook.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.DoctorSchedule;
import com.phenikaa.cse702051.medbook.model.MedicalService;
import com.phenikaa.cse702051.medbook.model.ScheduleBreak;
import com.phenikaa.cse702051.medbook.model.Specialty;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorScheduleRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.repository.ScheduleBreakRepository;
import com.phenikaa.cse702051.medbook.repository.SpecialtyRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * SV-2: khung giờ trống phụ thuộc loại dịch vụ và gợi ý giờ sớm nhất. CSDL dùng chung giữa các test, nên mỗi test
 * dựng bác sĩ, chuyên khoa và ngày riêng rồi luôn lọc theo bác sĩ hoặc chuyên khoa đó. Dịch vụ 30 phút là dịch vụ
 * mẫu số 1; dịch vụ 45 phút và dịch vụ đã ngừng do lớp test này tạo.
 */
class ServiceAwareSlotTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long SERVICE_30 = 1L; // seed: DV01 Khám tổng quát, 30 phút
    private static final AtomicInteger DAY = new AtomicInteger();

    private static Long service45;
    private static Long stoppedService;
    private static IsolatedPatient patient;
    private static IsolatedPatient other;

    @Autowired
    private AppointmentSlotRepository slots;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private DoctorRepository doctors;
    @Autowired
    private SpecialtyRepository specialties;
    @Autowired
    private MedicalServiceRepository services;
    @Autowired
    private DoctorScheduleRepository schedules;
    @Autowired
    private ScheduleBreakRepository breaks;

    private IsolatedDoctor doctor;
    /** Ngày riêng của từng test, trong cửa sổ 30 ngày của "giờ sớm nhất". */
    private LocalDate day;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    @BeforeEach
    void fixtures() {
        if (patient == null) {
            String key = unique();
            patient = data.isolatedPatient("sas" + key);
            other = data.isolatedPatient("saso" + key);
            service45 = services.save(MedicalService.builder().code("T45" + key).name("Khám thử nghiệm 45 phút")
                    .durationMinutes(45).price(new BigDecimal("300000.00")).status("ACTIVE").build()).getId();
            stoppedService = services.save(MedicalService.builder().code("TNG" + key).name("Dịch vụ thử nghiệm đã ngừng")
                    .durationMinutes(15).price(new BigDecimal("100000.00")).status("INACTIVE").build()).getId();
        }
        doctor = data.isolatedDoctor("sas" + unique());
        day = LocalDate.now().plusDays(5 + DAY.incrementAndGet() % 20);
    }

    // ---------- trợ giúp ----------

    private JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private AppointmentSlot slot(long doctorId, LocalDate date, String start, int minutes) {
        LocalTime from = LocalTime.parse(start);
        return slots.save(AppointmentSlot.builder().doctor(doctors.findById(doctorId).orElseThrow()).slotDate(date)
                .startTime(from).endTime(from.plusMinutes(minutes)).isAvailable(true).status("AVAILABLE").build());
    }

    private AppointmentSlot slot(String start, int minutes) {
        return slot(doctor.doctorId(), day, start, minutes);
    }

    private ResultActions available(Long serviceId) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/appointment-slots/available")
                .param("doctorId", String.valueOf(doctor.doctorId())).param("date", day.toString())
                .header("Authorization", patient.token());
        if (serviceId != null) {
            request.param("serviceId", String.valueOf(serviceId));
        }
        return mvc.perform(request);
    }

    private List<Long> ids(JsonNode list, String field) {
        List<Long> result = new ArrayList<>();
        list.forEach(item -> result.add(item.get(field).asLong()));
        return result;
    }

    private MockHttpServletRequestBuilder earliest(String token, long serviceId) {
        return get("/api/v1/appointment-slots/earliest").param("serviceId", String.valueOf(serviceId))
                .header("Authorization", token);
    }

    private ResultActions book(String token, long slotId, long serviceId) throws Exception {
        return mvc.perform(post("/api/v1/appointments").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":%d}".formatted(slotId, serviceId)));
    }

    private long appointmentsOn(long slotId) {
        return appointments.findAll().stream().filter(a -> a.getSlot().getId().equals(slotId)).count();
    }

    private Specialty specialty(String key) {
        return specialties.save(Specialty.builder().code("CK" + key).name("Chuyên khoa thử nghiệm " + key)
                .status("ACTIVE").build());
    }

    private void assign(long doctorId, Specialty specialty) {
        Doctor profile = doctors.findById(doctorId).orElseThrow();
        profile.setSpecialty(specialty);
        doctors.saveAndFlush(profile);
    }

    // ---------- danh sách giờ trống theo dịch vụ ----------

    @Test
    @DisplayName("AC-S2.1 Ca 30 phút: dịch vụ 30 phút ra đủ khung giờ, dịch vụ 45 phút ra danh sách rỗng, không truyền serviceId ra như cũ")
    void listingDependsOnServiceDuration() throws Exception {
        List<Long> all = List.of(slot("08:00", 30).getId(), slot("08:30", 30).getId(), slot("09:00", 30).getId());

        assertEquals(all, ids(json(available(null).andExpect(status().isOk())), "id"));
        assertEquals(all, ids(json(available(SERVICE_30).andExpect(status().isOk())), "id"));
        assertEquals(List.of(), ids(json(available(service45).andExpect(status().isOk())), "id"));
    }

    @Test
    @DisplayName("AC-S2.1 Ngày có khung giờ dài ngắn khác nhau: dịch vụ 45 phút chỉ thấy khung giờ từ 45 phút trở lên")
    void listingKeepsOnlySlotsLongEnough() throws Exception {
        long half = slot("08:00", 30).getId();
        long exact = slot("09:00", 45).getId();
        long longer = slot("10:00", 60).getId();

        assertEquals(List.of(exact, longer), ids(json(available(service45).andExpect(status().isOk())), "id"));
        assertEquals(List.of(half, exact, longer), ids(json(available(SERVICE_30).andExpect(status().isOk())), "id"));
    }

    @Test
    @DisplayName("Danh sách giờ trống với dịch vụ không tồn tại hoặc đã ngừng: 400 kèm details.serviceId")
    void listingRejectsUnknownOrStoppedService() throws Exception {
        slot("08:00", 30);

        available(999_999_999L).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.serviceId").isNotEmpty());
        available(stoppedService).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.serviceId").value("Dịch vụ khám đã ngừng sử dụng"));
    }

    // ---------- đặt và đổi lịch ----------

    @Test
    @DisplayName("AC-S2.2 Đặt dịch vụ 45 phút vào khung 30 phút: 422 nêu rõ số phút, không tạo lịch; vào khung 45 phút thì 201")
    void bookingRejectsAServiceLongerThanTheSlot() throws Exception {
        AppointmentSlot half = slot("08:00", 30);
        AppointmentSlot exact = slot("09:00", 45);

        book(patient.token(), half.getId(), service45).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Dịch vụ cần 45 phút, khung giờ này chỉ dài 30 phút"));
        mvc.perform(post("/api/v1/reception/appointments").header("Authorization", receptionistToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":%d,\"slotId\":%d,\"serviceId\":%d}".formatted(patient.patientId(),
                        half.getId(), service45)))
                .andExpect(status().isUnprocessableEntity());

        assertEquals(0, appointmentsOn(half.getId()));
        assertEquals("AVAILABLE", slots.findById(half.getId()).orElseThrow().getStatus());

        book(patient.token(), exact.getId(), service45).andExpect(status().isCreated());
        book(other.token(), half.getId(), SERVICE_30).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC-S2.2 Đổi lịch sang khung ngắn hơn thời lượng dịch vụ: 422, lịch cũ và hai khung giờ giữ nguyên (bệnh nhân và lễ tân)")
    void rescheduleRejectsASlotShorterThanTheService() throws Exception {
        AppointmentSlot current = slot("09:00", 45);
        AppointmentSlot shorter = slot("10:00", 30);
        AppointmentSlot fitting = slot("11:00", 45);
        long id = json(book(patient.token(), current.getId(), service45).andExpect(status().isCreated()))
                .get("id").asLong();

        mvc.perform(patch("/api/v1/appointments/" + id + "/reschedule").header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"newSlotId\":%d}".formatted(shorter.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Dịch vụ cần 45 phút, khung giờ này chỉ dài 30 phút"));
        mvc.perform(patch("/api/v1/reception/appointments/" + id + "/reschedule")
                .header("Authorization", receptionistToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newSlotId\":%d,\"reason\":\"đổi giờ\"}".formatted(shorter.getId())))
                .andExpect(status().isUnprocessableEntity());

        assertEquals(current.getId(), appointments.findById(id).orElseThrow().getSlot().getId());
        assertEquals("BOOKED", slots.findById(current.getId()).orElseThrow().getStatus());
        assertEquals("AVAILABLE", slots.findById(shorter.getId()).orElseThrow().getStatus());

        mvc.perform(patch("/api/v1/appointments/" + id + "/reschedule").header("Authorization", patient.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"newSlotId\":%d}".formatted(fitting.getId())))
                .andExpect(status().isOk());
    }

    // ---------- giờ sớm nhất ----------

    @Test
    @DisplayName("AC-S2.3 Giờ sớm nhất: đúng thứ tự thời gian qua nhiều bác sĩ, đủ trường của hợp đồng, chỉ khung giờ đủ dài")
    void earliestIsOrderedAcrossDoctors() throws Exception {
        String key = unique();
        Specialty specialty = specialty(key);
        IsolatedDoctor second = data.isolatedDoctor("sas2" + key);
        assign(doctor.doctorId(), specialty);
        assign(second.doctorId(), specialty);
        long c = slot(doctor.doctorId(), day.plusDays(1), "08:00", 30).getId();
        long a = slot(second.doctorId(), day, "08:00", 30).getId();
        long b = slot(doctor.doctorId(), day, "09:00", 45).getId();
        long d = slot(second.doctorId(), day.plusDays(1), "08:00", 60).getId();

        JsonNode result = json(mvc.perform(earliest(patient.token(), SERVICE_30)
                .param("specialtyId", String.valueOf(specialty.getId()))).andExpect(status().isOk()));

        assertEquals(List.of(a, b, c, d), ids(result, "slotId"), "ngày, giờ bắt đầu, rồi id");
        JsonNode first = result.get(0);
        assertEquals(second.doctorId(), first.get("doctorId").asLong());
        assertEquals("Bác sĩ thử nghiệm sas2" + key, first.get("doctorName").asText());
        assertEquals(specialty.getId(), first.get("specialtyId").asLong());
        assertEquals(specialty.getName(), first.get("specialtyName").asText());
        assertEquals(day.toString(), first.get("date").asText());
        assertEquals("08:00:00", first.get("startTime").asText());
        assertEquals("08:30:00", first.get("endTime").asText());

        JsonNode longOnly = json(mvc.perform(earliest(patient.token(), service45)
                .param("specialtyId", String.valueOf(specialty.getId()))).andExpect(status().isOk()));
        assertEquals(List.of(b, d), ids(longOnly, "slotId"));
    }

    @Test
    @DisplayName("AC-S2.3 Giờ sớm nhất lọc đúng theo chuyên khoa và theo bác sĩ")
    void earliestFiltersBySpecialtyAndDoctor() throws Exception {
        String key = unique();
        Specialty mine = specialty(key + "a");
        Specialty theirs = specialty(key + "b");
        IsolatedDoctor colleague = data.isolatedDoctor("sasc" + key);
        IsolatedDoctor outsider = data.isolatedDoctor("saso" + key);
        assign(doctor.doctorId(), mine);
        assign(colleague.doctorId(), mine);
        assign(outsider.doctorId(), theirs);
        long ofDoctor = slot(doctor.doctorId(), day, "08:00", 30).getId();
        long ofColleague = slot(colleague.doctorId(), day, "09:00", 30).getId();
        long ofOutsider = slot(outsider.doctorId(), day, "07:00", 30).getId();

        assertEquals(List.of(ofDoctor, ofColleague), ids(json(mvc.perform(earliest(patient.token(), SERVICE_30)
                .param("specialtyId", String.valueOf(mine.getId()))).andExpect(status().isOk())), "slotId"));
        assertEquals(List.of(ofOutsider), ids(json(mvc.perform(earliest(patient.token(), SERVICE_30)
                .param("specialtyId", String.valueOf(theirs.getId()))).andExpect(status().isOk())), "slotId"));
        assertEquals(List.of(ofColleague), ids(json(mvc.perform(earliest(patient.token(), SERVICE_30)
                .param("doctorId", String.valueOf(colleague.doctorId()))).andExpect(status().isOk())), "slotId"));
        assertEquals(List.of(), ids(json(mvc.perform(earliest(patient.token(), SERVICE_30)
                .param("doctorId", String.valueOf(colleague.doctorId()))
                .param("specialtyId", String.valueOf(theirs.getId()))).andExpect(status().isOk())), "slotId"));
    }

    @Test
    @DisplayName("AC-S2.3 Giờ sớm nhất bỏ khung giờ đã qua, đã đặt, đã gỡ, chạm giờ nghỉ, đang bị người khác giữ; khung giờ do chính mình giữ vẫn có")
    void earliestSkipsSlotsThatCannotBeBooked() throws Exception {
        slot(doctor.doctorId(), LocalDate.now().minusDays(1), "08:00", 30);
        AppointmentSlot booked = slot("08:00", 30);
        book(other.token(), booked.getId(), SERVICE_30).andExpect(status().isCreated());
        AppointmentSlot removed = slot("08:30", 30);
        removed.setStatus("CANCELLED");
        removed.setIsAvailable(false);
        slots.saveAndFlush(removed);
        slot("12:00", 30); // nằm trong giờ nghỉ trưa của ca
        DoctorSchedule shift = schedules.save(DoctorSchedule.builder()
                .doctor(doctors.findById(doctor.doctorId()).orElseThrow()).workDate(day)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).slotMinutes(30).build());
        breaks.save(ScheduleBreak.builder().doctorSchedule(shift).startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0)).reason("Nghỉ trưa").build());
        AppointmentSlot heldByOther = slot("14:00", 30);
        mvc.perform(post("/api/v1/appointment-slots/" + heldByOther.getId() + "/hold")
                .header("Authorization", other.token())).andExpect(status().isOk());
        AppointmentSlot heldByMe = slot("15:00", 30);
        mvc.perform(post("/api/v1/appointment-slots/" + heldByMe.getId() + "/hold")
                .header("Authorization", patient.token())).andExpect(status().isOk());
        long free = slot("16:00", 30).getId();

        JsonNode result = json(mvc.perform(earliest(patient.token(), SERVICE_30)
                .param("doctorId", String.valueOf(doctor.doctorId())).param("limit", "20"))
                .andExpect(status().isOk()));

        assertEquals(List.of(heldByMe.getId(), free), ids(result, "slotId"));
    }

    @Test
    @DisplayName("AC-S2.3 Bác sĩ ngừng nhận lịch không có trong giờ sớm nhất")
    void earliestSkipsInactiveDoctors() throws Exception {
        long id = slot("08:00", 30).getId();
        assertEquals(List.of(id), ids(json(mvc.perform(earliest(patient.token(), SERVICE_30)
                .param("doctorId", String.valueOf(doctor.doctorId()))).andExpect(status().isOk())), "slotId"));

        Doctor profile = doctors.findById(doctor.doctorId()).orElseThrow();
        profile.setIsActive(false);
        doctors.saveAndFlush(profile);

        assertEquals(List.of(), ids(json(mvc.perform(earliest(patient.token(), SERVICE_30)
                .param("doctorId", String.valueOf(doctor.doctorId()))).andExpect(status().isOk())), "slotId"));
    }

    @Test
    @DisplayName("AC-S2.3 limit mặc định 5, lớn hơn 20 bị cắt còn 20; from lùi mốc bắt đầu; ngoài 30 ngày kể từ from thì không lấy")
    void earliestHonoursLimitAndWindow() throws Exception {
        LocalTime start = LocalTime.of(7, 0);
        for (int i = 0; i < 24; i++) {
            slot(doctor.doctorId(), day, start.plusMinutes(30L * i).toString(), 30);
        }
        long nextDay = slot(doctor.doctorId(), day.plusDays(1), "08:00", 30).getId();
        slot(doctor.doctorId(), day.plusDays(40), "08:00", 30); // quá 30 ngày kể từ hôm nay lẫn kể từ from
        String doctorId = String.valueOf(doctor.doctorId());

        assertEquals(5, json(mvc.perform(earliest(patient.token(), SERVICE_30).param("doctorId", doctorId))
                .andExpect(status().isOk())).size());
        assertEquals(20, json(mvc.perform(earliest(patient.token(), SERVICE_30).param("doctorId", doctorId)
                .param("limit", "500")).andExpect(status().isOk())).size());
        assertEquals(1, json(mvc.perform(earliest(patient.token(), SERVICE_30).param("doctorId", doctorId)
                .param("limit", "0")).andExpect(status().isOk())).size());

        JsonNode later = json(mvc.perform(earliest(patient.token(), SERVICE_30).param("doctorId", doctorId)
                .param("from", day.plusDays(1).toString()).param("limit", "20")).andExpect(status().isOk()));
        assertEquals(List.of(nextDay), ids(later, "slotId"), "bỏ ngày trước from, và bỏ khung giờ cách hơn 30 ngày");

        JsonNode fromThePast = json(mvc.perform(earliest(patient.token(), SERVICE_30).param("doctorId", doctorId)
                .param("from", "2000-01-01").param("limit", "20")).andExpect(status().isOk()));
        assertEquals(20, fromThePast.size(), "from trong quá khứ được coi là hôm nay");
        assertFalse(ids(fromThePast, "slotId").contains(nextDay));
    }

    @Test
    @DisplayName("AC-S2.4 Giờ sớm nhất: thiếu, sai hoặc ngừng serviceId 400; khách 401; bác sĩ và quản trị viên 403; lễ tân 200")
    void earliestValidatesInputAndRoles() throws Exception {
        String url = "/api/v1/appointment-slots/earliest";
        mvc.perform(get(url).header("Authorization", patient.token())).andExpect(status().isBadRequest());
        mvc.perform(get(url).param("serviceId", "abc").header("Authorization", patient.token()))
                .andExpect(status().isBadRequest());
        mvc.perform(earliest(patient.token(), 999_999_999L)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.serviceId").value("Dịch vụ khám không tồn tại"));
        mvc.perform(earliest(patient.token(), stoppedService)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.serviceId").isNotEmpty());

        mvc.perform(get(url).param("serviceId", "1")).andExpect(status().isUnauthorized());
        mvc.perform(earliest(doctorToken(), SERVICE_30)).andExpect(status().isForbidden());
        mvc.perform(earliest(doctor.token(), SERVICE_30)).andExpect(status().isForbidden());
        mvc.perform(earliest(adminToken(), SERVICE_30)).andExpect(status().isForbidden());
        mvc.perform(earliest(receptionistToken(), SERVICE_30)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC-S2.5 Dữ liệu mẫu: cả bốn dịch vụ mẫu đều có ít nhất một bác sĩ mẫu với khung giờ đặt được")
    void everySeededServiceIsBookableWithASeededDoctor() throws Exception {
        for (long serviceId = 1; serviceId <= 4; serviceId++) {
            MedicalService service = services.findById(serviceId).orElseThrow();
            boolean bookable = slots.findAll().stream()
                    .filter(s -> s.getId() < 1000) // khung giờ mẫu trong data-h2.sql có id cố định dưới 1000
                    .anyMatch(s -> java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes()
                            >= service.getDurationMinutes());
            assertTrue(bookable, "dịch vụ mẫu " + service.getCode() + " (" + service.getDurationMinutes()
                    + " phút) không có khung giờ mẫu nào đủ dài");
        }
    }
}
