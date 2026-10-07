package com.phenikaa.cse702051.medbook.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Specialty;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.SpecialtyRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * AC-02.1: trang công khai chỉ thấy bác sĩ đang hoạt động, tìm theo tên hoặc chuyên khoa, có phân trang, và JSON
 * không bao giờ chứa tài khoản, số giấy phép hay số điện thoại.
 */
class DoctorPublicApiTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "/api/v1/doctors";

    @Autowired
    private DoctorRepository doctors;
    @Autowired
    private SpecialtyRepository specialties;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private Doctor doctor(String token, String suffix, String fullName, Specialty specialty, boolean active) {
        User user = data.user("pubdoc_" + token.toLowerCase() + suffix, "Iso#Pass123", "DOCTOR");
        return doctors.save(Doctor.builder()
                .userId(user.getId())
                .specialty(specialty)
                .fullName(fullName)
                .phone("0977000111")
                .licenseNumber("LIC-" + token + suffix)
                .bio("Giới thiệu " + suffix)
                .isActive(active)
                .build());
    }

    private JsonNode search(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    @Test
    @DisplayName("Tìm theo tên: không cần đăng nhập, không phân biệt hoa thường, chỉ bác sĩ đang hoạt động, theo tên tăng dần")
    void searchesActiveDoctorsByName() throws Exception {
        String token = unique();
        Doctor binh = doctor(token, "b", "Trần Bình " + token, null, true);
        Doctor an = doctor(token, "a", "Nguyễn An " + token, null, true);
        doctor(token, "x", "Lê Nghỉ " + token, null, false);

        JsonNode page = search(get(URL).param("keyword", token.toLowerCase()));
        assertEquals(List.of(an.getId(), binh.getId()), ids(page), "theo họ tên tăng dần, không có bác sĩ đã ngừng");
        assertEquals(2, page.get("totalElements").asInt());
        assertEquals(0, page.get("page").asInt());
        assertEquals(20, page.get("size").asInt());
        assertEquals("Nguyễn An " + token, page.get("content").get(0).get("fullName").asText());
        assertEquals("Giới thiệu a", page.get("content").get(0).get("bio").asText());

        assertEquals(List.of(binh.getId()), ids(search(get(URL).param("keyword", "bình " + token.toLowerCase()))));
        assertEquals(List.of(), ids(search(get(URL).param("keyword", "khongco" + token))));
        // % là ký tự thường: không khớp mọi bác sĩ
        assertEquals(0, search(get(URL).param("keyword", "%")).get("totalElements").asInt());
    }

    @Test
    @DisplayName("Tìm theo tên chuyên khoa và lọc theo specialtyId, kèm tên chuyên khoa trong kết quả")
    void searchesBySpecialty() throws Exception {
        String token = unique();
        Specialty cardio = specialties.save(Specialty.builder().code("DPC" + token).name("Tim mạch " + token)
                .status("ACTIVE").build());
        Specialty derma = specialties.save(Specialty.builder().code("DPD" + token).name("Da liễu " + token)
                .status("ACTIVE").build());
        Doctor heart = doctor(token, "h", "Bác sĩ Một", cardio, true);
        Doctor skin = doctor(token, "s", "Bác sĩ Hai", derma, true);
        doctor(token, "n", "Bác sĩ Ba", null, true);

        JsonNode byName = search(get(URL).param("keyword", "tim mạch " + token.toLowerCase()));
        assertEquals(List.of(heart.getId()), ids(byName));
        assertEquals(cardio.getId(), byName.get("content").get(0).get("specialtyId").asLong());
        assertEquals("Tim mạch " + token, byName.get("content").get(0).get("specialtyName").asText());

        assertEquals(List.of(skin.getId()), ids(search(get(URL).param("specialtyId", String.valueOf(derma.getId())))));
        // kết hợp chuyên khoa và từ khóa
        assertEquals(List.of(), ids(search(get(URL).param("specialtyId", String.valueOf(derma.getId()))
                .param("keyword", "Một"))));
        assertEquals(List.of(), ids(search(get(URL).param("specialtyId", "999999"))));
    }

    @Test
    @DisplayName("Phân trang và sắp xếp: chia trang đúng, size bị cắt còn 100, sort chỉ nhận fullName hoặc id")
    void paginatesAndWhitelistsSort() throws Exception {
        String token = unique();
        Doctor first = doctor(token, "1", "Anh " + token, null, true);
        Doctor second = doctor(token, "2", "Bảo " + token, null, true);
        Doctor third = doctor(token, "3", "Cường " + token, null, true);

        JsonNode pageOne = search(get(URL).param("keyword", token).param("size", "2"));
        assertEquals(List.of(first.getId(), second.getId()), ids(pageOne));
        assertEquals(3, pageOne.get("totalElements").asInt());
        assertEquals(2, pageOne.get("totalPages").asInt());
        assertEquals(List.of(third.getId()), ids(search(get(URL).param("keyword", token).param("size", "2")
                .param("page", "1"))));

        assertEquals(List.of(third.getId(), second.getId(), first.getId()),
                ids(search(get(URL).param("keyword", token).param("sort", "fullName,desc"))));
        assertEquals(List.of(third.getId(), second.getId(), first.getId()),
                ids(search(get(URL).param("keyword", token).param("sort", "id,desc"))));
        assertEquals(100, search(get(URL).param("size", "1000")).get("size").asInt());

        mvc.perform(get(URL).param("sort", "licenseNumber")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.sort").exists());
        mvc.perform(get(URL).param("sort", "fullName,ngang")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Chi tiết và danh sách không lộ userId, licenseNumber, phone; bác sĩ ngừng hoạt động → 404")
    void neverExposesInternalFields() throws Exception {
        String token = unique();
        Doctor active = doctor(token, "v", "Công khai " + token, null, true);
        Doctor inactive = doctor(token, "i", "Đã nghỉ " + token, null, false);

        MvcResult detail = mvc.perform(get(URL + "/" + active.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(active.getId()))
                .andExpect(jsonPath("$.fullName").value("Công khai " + token))
                .andExpect(jsonPath("$.bio").value("Giới thiệu v"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.licenseNumber").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.specialty").doesNotExist())
                .andExpect(jsonPath("$.isActive").doesNotExist())
                .andReturn();
        MvcResult list = mvc.perform(get(URL).param("keyword", token)).andExpect(status().isOk()).andReturn();
        for (MvcResult result : List.of(detail, list)) {
            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertFalse(body.contains("LIC-" + token), "số giấy phép không được lộ: " + body);
            assertFalse(body.contains("0977000111"), "số điện thoại không được lộ: " + body);
            assertFalse(body.contains("userId") || body.contains("licenseNumber") || body.contains("phone"), body);
        }

        mvc.perform(get(URL + "/" + inactive.getId())).andExpect(status().isNotFound());
        mvc.perform(get(URL + "/999999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-02.7 Đường dẫn cũ /doctors/admin* đã bị gỡ; đường dẫn công khai chỉ đọc")
    void legacyAdminPathsAreGone() throws Exception {
        String body = "{\"fullName\":\"Không được tạo\",\"userId\":2}";
        int created = mvc.perform(post(URL + "/admin").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        assertEquals(true, created == 404 || created == 405, "POST /doctors/admin phải không còn: " + created);
        mvc.perform(put(URL + "/admin/1").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(delete(URL + "/admin/1").header("Authorization", adminToken()))
                .andExpect(status().isNotFound());
        // người không phải Admin vẫn bị chặn ngay ở tầng route
        mvc.perform(post(URL + "/admin").header("Authorization", doctorToken())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        // bác sĩ seed không bị ảnh hưởng
        mvc.perform(get(URL + "/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }
}
