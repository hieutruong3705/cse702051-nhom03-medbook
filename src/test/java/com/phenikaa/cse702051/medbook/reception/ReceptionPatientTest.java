package com.phenikaa.cse702051.medbook.reception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.MedicalRecordRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedReceptionist;

/**
 * BE-2: bệnh nhân tại quầy lễ tân. Tra cứu theo họ tên, mã, điện thoại; tạo hồ sơ không gắn tài khoản; chỉ trả
 * trường hành chính; chỉ lễ tân gọi được.
 */
class ReceptionPatientTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "/api/v1/reception/patients";

    @Autowired
    private PatientRepository patients;
    @Autowired
    private MedicalRecordRepository medicalRecords;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    /** Số điện thoại 10 chữ số, khác nhau giữa các lần gọi. */
    private static String phone() {
        return "09" + String.format("%08d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000L));
    }

    private static String body(String fullName, String dateOfBirth, String genderCode, String phone, String email,
            String address) {
        StringBuilder json = new StringBuilder("{");
        append(json, "fullName", fullName);
        append(json, "dateOfBirth", dateOfBirth);
        append(json, "genderCode", genderCode);
        append(json, "phone", phone);
        append(json, "email", email);
        append(json, "address", address);
        if (json.charAt(json.length() - 1) == ',') {
            json.setLength(json.length() - 1);
        }
        return json.append('}').toString();
    }

    private static void append(StringBuilder json, String field, String value) {
        if (value != null) {
            json.append('"').append(field).append("\":\"").append(value).append("\",");
        }
    }

    private ResultActions create(String token, String body) throws Exception {
        return mvc.perform(post(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode createOk(String fullName, String phone) throws Exception {
        return json(create(receptionistToken(), body(fullName, "1990-05-20", "MALE", phone, null, null))
                .andExpect(status().isCreated()));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions search(String keyword) throws Exception {
        return mvc.perform(get(URL).param("keyword", keyword).header("Authorization", receptionistToken()));
    }

    // ---------- tạo hồ sơ ----------

    @Test
    @DisplayName("AC-B2.3 Tạo hồ sơ hợp lệ: 201, mã BN + sáu chữ số, không có tài khoản, có bệnh án, một bản nhật ký của lễ tân")
    void createsPatientWithoutAccount() throws Exception {
        IsolatedReceptionist receptionist = data.isolatedReceptionist("pcreate" + unique());
        String marker = unique();
        String phone = phone();
        String fullName = "Đỗ Thị Quầy " + marker;

        ResultActions result = create(receptionist.token(),
                body("  " + fullName + "  ", "1988-03-15", "female", " " + phone + " ", "Quay." + marker
                        + "@Example.COM", "12 Đường Thử Nghiệm"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value(fullName))
                .andExpect(jsonPath("$.dateOfBirth").value("1988-03-15"))
                .andExpect(jsonPath("$.genderCode").value("FEMALE"))
                .andExpect(jsonPath("$.phone").value(phone))
                .andExpect(jsonPath("$.email").value("quay." + marker + "@example.com"))
                .andExpect(jsonPath("$.address").value("12 Đường Thử Nghiệm"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.hasAccount").value(false));
        JsonNode created = json(result);
        long id = created.get("id").asLong();
        result.andExpect(header().string("Location", URL + "/" + id));

        assertTrue(created.get("patientCode").asText().matches("BN\\d{6}"), created.get("patientCode").asText());
        Patient saved = patients.findById(id).orElseThrow();
        assertNull(saved.getUser(), "hồ sơ tạo tại quầy không gắn tài khoản");
        assertTrue(medicalRecords.findByPatientId(id).isPresent(), "bệnh nhân mới phải có bệnh án rỗng");

        assertEquals(1, countAudits(AuditActions.ENTITY_PATIENTS, id, AuditActions.PATIENT_CREATE,
                receptionist.userId()));
        assertFalse(auditsOf(AuditActions.ENTITY_PATIENTS, id).stream()
                .anyMatch(a -> a.getMetadataJson() != null
                        && (a.getMetadataJson().contains(marker) || a.getMetadataJson().contains(phone))),
                "nhật ký không chứa họ tên hay điện thoại");
    }

    @Test
    @DisplayName("AC-B2.3 Email và địa chỉ để trống được lưu là rỗng, không phải chuỗi trắng")
    void blankOptionalFieldsBecomeNull() throws Exception {
        JsonNode created = json(create(receptionistToken(),
                body("Lý Văn Trống " + unique(), "2001-01-01", "OTHER", phone(), "", "   "))
                .andExpect(status().isCreated()));

        Patient saved = patients.findById(created.get("id").asLong()).orElseThrow();
        assertNull(saved.getEmail());
        assertNull(saved.getAddress());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "thiếu họ tên            | null    | 1990-01-01 | MALE   | 0912345678 | null       | fullName",
            "họ tên chỉ có dấu cách  | '   '   | 1990-01-01 | MALE   | 0912345678 | null       | fullName",
            "thiếu ngày sinh         | Nguyễn A| null       | MALE   | 0912345678 | null       | dateOfBirth",
            "ngày sinh ở tương lai   | Nguyễn A| 2999-01-01 | MALE   | 0912345678 | null       | dateOfBirth",
            "thiếu giới tính         | Nguyễn A| 1990-01-01 | null   | 0912345678 | null       | genderCode",
            "giới tính lạ            | Nguyễn A| 1990-01-01 | KHAC   | 0912345678 | null       | genderCode",
            "thiếu điện thoại        | Nguyễn A| 1990-01-01 | MALE   | null       | null       | phone",
            "điện thoại quá ngắn     | Nguyễn A| 1990-01-01 | MALE   | 12345      | null       | phone",
            "điện thoại có chữ       | Nguyễn A| 1990-01-01 | MALE   | 09abc45678 | null       | phone",
            "email sai định dạng     | Nguyễn A| 1990-01-01 | MALE   | 0912345678 | khong-phai | email"
    })
    @DisplayName("AC-B2.4 Dữ liệu sai trả 400 với details đúng tên trường")
    void invalidDataIsRejectedPerField(String name, String fullName, String dateOfBirth, String genderCode,
            String phone, String email, String field) throws Exception {
        long before = patients.count();

        create(receptionistToken(), body(fullName, dateOfBirth, genderCode, phone, email, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details." + field).isNotEmpty());

        assertEquals(before, patients.count(), "không tạo hồ sơ nào khi dữ liệu sai");
    }

    @Test
    @DisplayName("AC-B2.4 Trùng họ tên (khác hoa thường), ngày sinh và điện thoại: 409 tại trường phone, không tạo thêm dòng")
    void duplicatePatientIsRejected() throws Exception {
        String fullName = "Trùng Hồ Sơ " + unique();
        String phone = phone();
        create(receptionistToken(), body(fullName, "1975-12-31", "MALE", phone, null, null))
                .andExpect(status().isCreated());
        long before = patients.count();

        create(receptionistToken(), body(fullName.toUpperCase(), "1975-12-31", "FEMALE", phone, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.phone").isNotEmpty());
        assertEquals(before, patients.count());

        // khác một trong ba trường thì là người khác: tạo được
        create(receptionistToken(), body(fullName, "1975-12-30", "MALE", phone, null, null))
                .andExpect(status().isCreated());
        create(receptionistToken(), body(fullName, "1975-12-31", "MALE", phone(), null, null))
                .andExpect(status().isCreated());
    }

    // ---------- tra cứu ----------

    @Test
    @DisplayName("AC-B2.1 Tìm theo họ tên, mã bệnh nhân, điện thoại đều ra đúng; bệnh nhân có tài khoản hiện hasAccount = true")
    void searchFindsByNameCodeAndPhone() throws Exception {
        String marker = unique();
        String phone = phone();
        JsonNode created = createOk("Tìm Kiếm " + marker, phone);
        long id = created.get("id").asLong();

        search("kiếm " + marker.toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        search(created.get("patientCode").asText().toLowerCase())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        search(phone.substring(2))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        search("khong-co-ai-ten-" + marker).andExpect(jsonPath("$.totalElements").value(0));

        IsolatedPatient withAccount = data.isolatedPatient("rcpacc" + marker);
        search("rcpacc" + marker)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(withAccount.patientId()))
                .andExpect(jsonPath("$.content[0].hasAccount").value(true));
    }

    @Test
    @DisplayName("AC-B2.1 Ký tự % và _ trong từ khóa là ký tự thường: không trả toàn bộ, và tìm đúng tên có chứa %")
    void wildcardCharactersAreLiteral() throws Exception {
        String marker = unique();
        long id = createOk("Giảm 100% phí " + marker, phone()).get("id").asLong();
        createOk("Không liên quan " + marker, phone());
        long all = json(search("")).get("totalElements").asLong();

        assertTrue(json(search("%")).get("totalElements").asLong() < all, "% không khớp mọi bản ghi");
        assertTrue(json(search("_")).get("totalElements").asLong() < all, "_ không khớp mọi bản ghi");
        search("100% phí " + marker)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    @DisplayName("AC-B2.1 Phân trang: size lớn hơn 100 bị cắt, page âm coi là 0, thứ tự theo họ tên rồi id ổn định giữa các trang")
    void paginationIsBoundedAndStable() throws Exception {
        String marker = unique();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ids.add(createOk("Cùng Tên " + marker, phone()).get("id").asLong());
        }
        long firstByName = createOk("An Đứng Đầu " + marker, phone()).get("id").asLong();

        mvc.perform(get(URL).param("size", "1000").param("page", "-5").header("Authorization", receptionistToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.page").value(0));

        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            JsonNode result = json(mvc.perform(get(URL).param("keyword", marker).param("size", "1")
                    .param("page", String.valueOf(page)).header("Authorization", receptionistToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(4)));
            seen.add(result.get("content").get(0).get("id").asLong());
        }
        assertEquals(List.of(firstByName, ids.get(0), ids.get(1), ids.get(2)), seen);
    }

    @Test
    @DisplayName("AC-B2.2 Kết quả không có dị ứng, nhóm máu, liên hệ khẩn cấp dù hồ sơ có các thông tin đó")
    void responseHasNoMedicalFields() throws Exception {
        long id = createOk("Có Thông Tin Y Tế " + unique(), phone()).get("id").asLong();
        Patient patient = patients.findById(id).orElseThrow();
        patient.setAllergies("Dị ứng penicillin");
        patient.setBloodType("O+");
        patient.setEmergencyContactName("Người Nhà");
        patient.setEmergencyContactPhone("0900000009");
        patients.save(patient);

        String detail = mvc.perform(get(URL + "/" + id).header("Authorization", receptionistToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.allergies").doesNotExist())
                .andExpect(jsonPath("$.bloodType").doesNotExist())
                .andExpect(jsonPath("$.emergencyContactName").doesNotExist())
                .andExpect(jsonPath("$.emergencyContactPhone").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertFalse(detail.contains("penicillin"));

        String list = search(patient.getPatientCode()).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertFalse(list.contains("penicillin") || list.contains("Người Nhà"));
    }

    @Test
    @DisplayName("Xem một hồ sơ: bệnh nhân không tồn tại trả 404")
    void unknownPatientReturns404() throws Exception {
        mvc.perform(get(URL + "/999999999").header("Authorization", receptionistToken()))
                .andExpect(status().isNotFound());
    }

    // ---------- quyền ----------

    @Test
    @DisplayName("AC-B2.5 Khách 401; bệnh nhân, bác sĩ, quản trị viên 403 ở cả ba điểm cuối")
    void onlyReceptionistsMayUseTheEndpoints() throws Exception {
        long id = createOk("Kiểm Quyền " + unique(), phone()).get("id").asLong();
        String valid = body("Không Được Tạo " + unique(), "1990-01-01", "MALE", phone(), null, null);
        long before = patients.count();

        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized());

        for (String token : List.of(patientToken(), doctorToken(), adminToken())) {
            mvc.perform(get(URL).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get(URL + "/" + id).header("Authorization", token)).andExpect(status().isForbidden());
            create(token, valid).andExpect(status().isForbidden());
        }
        assertEquals(before, patients.count());
    }

    @Test
    @DisplayName("AC-B2.3 Hồ sơ tạo tại quầy đặt ngày sinh đúng như đã nhập, không dùng giá trị mặc định của luồng đăng ký")
    void keepsEnteredBirthDate() throws Exception {
        JsonNode created = json(create(receptionistToken(),
                body("Ngày Sinh Riêng " + unique(), "1960-07-04", "MALE", phone(), null, null))
                .andExpect(status().isCreated()));

        assertEquals(LocalDate.of(1960, 7, 4),
                patients.findById(created.get("id").asLong()).orElseThrow().getDateOfBirth());
    }
}
