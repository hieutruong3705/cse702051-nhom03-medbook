package com.phenikaa.cse702051.medbook.doctor;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.phenikaa.cse702051.medbook.repository.MediaImageRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/** Ảnh đại diện bác sĩ và ảnh chuyên khoa: ai được tải, loại tệp nào được nhận, ảnh được trả công khai ra sao. */
class MediaImageApiTest extends AbstractApiTest {

    /** PNG 1x1 hợp lệ. */
    static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
    static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F' };

    @Autowired
    MediaImageRepository images;

    @AfterEach
    void removeImages() throws Exception {
        mvc.perform(delete("/api/v1/admin/doctors/1/avatar").header("Authorization", adminToken()));
        mvc.perform(delete("/api/v1/admin/doctors/2/avatar").header("Authorization", adminToken()));
        mvc.perform(delete("/api/v1/admin/specialties/1/image").header("Authorization", adminToken()));
    }

    private ResultActions upload(String url, String token, String name, byte[] content) throws Exception {
        return mvc.perform(multipart(url).file(new MockMultipartFile("file", name, "image/png", content))
                .header("Authorization", token));
    }

    @Test
    @DisplayName("Bác sĩ tự tải ảnh đại diện: trang công khai có avatarUrl, ảnh trả về đúng loại, lưu đệm lâu, có nhật ký")
    void doctorUploadsOwnAvatar() throws Exception {
        String body = upload("/api/v1/doctors/me/avatar", doctorToken(), "anh.png", PNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl", startsWith("/api/v1/images/")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.avatarUrl");

        mvc.perform(get("/api/v1/doctors/1")).andExpect(jsonPath("$.avatarUrl").value(url));
        byte[] served = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(PNG, served);
        assertTrue(countAudits(AuditActions.ENTITY_DOCTORS, 1L, AuditActions.DOCTOR_AVATAR_UPDATE, DOCTOR1_USER_ID) >= 1);
    }

    @Test
    @DisplayName("Đổi ảnh thì ảnh cũ bị xóa; gỡ ảnh thì avatarUrl về null")
    void replacingDeletesOldImageAndRemovingClearsUrl() throws Exception {
        long before = images.count();
        String first = JsonPath.read(upload("/api/v1/admin/doctors/1/avatar", adminToken(), "a.png", PNG)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.avatarUrl");
        String second = JsonPath.read(upload("/api/v1/admin/doctors/1/avatar", adminToken(), "b.jpg", JPEG)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.avatarUrl");

        assertEquals(before + 1, images.count(), "chỉ còn ảnh mới nhất");
        mvc.perform(get(first)).andExpect(status().isNotFound());
        mvc.perform(get(second)).andExpect(header().string("Content-Type", "image/jpeg"));

        mvc.perform(delete("/api/v1/admin/doctors/1/avatar").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl", nullValue()));
        assertEquals(before, images.count());
    }

    @Test
    @DisplayName("Tệp không phải JPEG/PNG/WebP bị từ chối dù đặt đuôi .png (HTML, SVG); ảnh quá 1 MB bị từ chối")
    void rejectsNonImagesAndLargeFiles() throws Exception {
        long before = images.count();
        upload("/api/v1/doctors/me/avatar", doctorToken(), "x.png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType());
        upload("/api/v1/doctors/me/avatar", doctorToken(), "x.svg",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType());
        byte[] big = new byte[1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        upload("/api/v1/doctors/me/avatar", doctorToken(), "big.png", big).andExpect(status().isPayloadTooLarge());
        upload("/api/v1/doctors/me/avatar", doctorToken(), "empty.png", new byte[0]).andExpect(status().isBadRequest());
        assertEquals(before, images.count(), "không lưu tệp bị từ chối");
    }

    @Test
    @DisplayName("Phân quyền: bệnh nhân, lễ tân không tải được; khách không tải được; bác sĩ không vào được API quản trị")
    void onlyAdminsAndDoctorsCanUpload() throws Exception {
        upload("/api/v1/doctors/me/avatar", patientToken(), "a.png", PNG).andExpect(status().isForbidden());
        upload("/api/v1/doctors/me/avatar", receptionistToken(), "a.png", PNG).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/doctors/me/avatar").file(new MockMultipartFile("file", "a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
        upload("/api/v1/admin/doctors/2/avatar", doctorToken(), "a.png", PNG).andExpect(status().isForbidden());
        upload("/api/v1/admin/specialties/1/image", doctorToken(), "a.png", PNG).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/doctors/2")).andExpect(jsonPath("$.avatarUrl", nullValue()));
    }

    @Test
    @DisplayName("Quản trị viên tải ảnh chuyên khoa: danh sách công khai có imageUrl, có nhật ký")
    void adminUploadsSpecialtyImage() throws Exception {
        String url = JsonPath.read(upload("/api/v1/admin/specialties/1/image", adminToken(), "s.png", PNG)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.imageUrl");
        mvc.perform(get("/api/v1/specialties/1")).andExpect(jsonPath("$.imageUrl").value(url));
        mvc.perform(get(url)).andExpect(status().isOk());
        assertTrue(countAudits(AuditActions.ENTITY_SPECIALTIES, 1L, AuditActions.SPECIALTY_IMAGE_UPDATE, ADMIN_USER_ID) >= 1);
    }

    @Test
    @DisplayName("Ảnh không tồn tại trả 404")
    void missingImageIs404() throws Exception {
        mvc.perform(get("/api/v1/images/987654")).andExpect(status().isNotFound());
    }
}
