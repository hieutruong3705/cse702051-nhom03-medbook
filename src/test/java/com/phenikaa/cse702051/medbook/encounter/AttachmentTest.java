package com.phenikaa.cse702051.medbook.encounter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.repository.AttachmentRepository;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * BE-04 (mục 7): tệp đính kèm an toàn — quyền theo bản ghi, kiểm tra chữ ký tệp/kích thước, không lộ đường
 * dẫn, audit tải xuống/xóa, không để tệp vật lý mồ côi.
 */
class AttachmentTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"
            .getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired
    private EncounterRepository encounters;

    @Autowired
    private AttachmentRepository attachments;

    @Value("${medbook.upload-dir}")
    private String uploadDir;

    /** Lần khám OPEN của bác sĩ {@code doc} với bệnh nhân {@code pat}. */
    private Encounter openEncounter(IsolatedDoctor doc, IsolatedPatient pat) {
        return data.openEncounter(doc.doctorId(), pat.patientId());
    }

    private ResultActions upload(String token, long encounterId, String fileName, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/octet-stream", content);
        return mvc.perform(multipart("/api/v1/encounters/" + encounterId + "/attachments").file(file)
                .header("Authorization", token));
    }

    private JsonNode uploadOk(String token, long encounterId, String fileName, byte[] content) throws Exception {
        MvcResult result = upload(token, encounterId, fileName, content).andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private Path storedFile(long attachmentId) {
        var attachment = attachments.findById(attachmentId).orElseThrow();
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve(attachment.getFilePath());
    }

    // ---------- tải lên ----------

    @Test
    @DisplayName("Bác sĩ phụ trách tải PDF/PNG/JPG lên: 201, DTO không lộ đường dẫn, tên lưu là UUID, có audit")
    void doctorUploadsSupportedFiles() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("att_upload");
        IsolatedPatient pat = data.isolatedPatient("att_upload");
        Encounter encounter = openEncounter(doc, pat);

        JsonNode pdf = uploadOk(doc.token(), encounter.getId(), "Ket qua xet nghiem.PDF", PDF);
        assertEquals("application/pdf", pdf.get("mimeType").asText());
        assertEquals(PDF.length, pdf.get("fileSize").asLong());
        assertEquals("Ket qua xet nghiem.PDF", pdf.get("originalFileName").asText());
        assertFalse(pdf.has("filePath"));
        assertFalse(pdf.has("storedFileName"));

        assertEquals("image/png", uploadOk(doc.token(), encounter.getId(), "x-quang.png", PNG).get("mimeType").asText());
        assertEquals("image/jpeg", uploadOk(doc.token(), encounter.getId(), "anh.jpeg", JPEG).get("mimeType").asText());

        long id = pdf.get("id").asLong();
        var stored = attachments.findById(id).orElseThrow();
        assertTrue(stored.getStoredFileName().matches("[0-9a-f-]{36}\\.pdf"), stored.getStoredFileName());
        assertTrue(Files.isRegularFile(storedFile(id)));
        assertArrayEquals(PDF, Files.readAllBytes(storedFile(id)));
        assertEquals(1, countAudits(AuditActions.ENTITY_ATTACHMENTS, id, AuditActions.ATTACHMENT_UPLOAD, doc.userId()));
    }

    @Test
    @DisplayName("Sai chữ ký hoặc đuôi giả mạo → 415; tệp rỗng → 400; quá 10 MB → 413; không lưu gì")
    void rejectsBadFiles() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("att_bad");
        IsolatedPatient pat = data.isolatedPatient("att_bad");
        Encounter encounter = openEncounter(doc, pat);
        long before = attachments.findByEncounterId(encounter.getId()).size();

        // nội dung văn bản nhưng đặt đuôi .pdf
        upload(doc.token(), encounter.getId(), "gia-mao.pdf", "Đây không phải PDF".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        // mã thực thi/script đặt đuôi ảnh
        upload(doc.token(), encounter.getId(), "shell.png", "<?php system($_GET['c']); ?>".getBytes())
                .andExpect(status().isUnsupportedMediaType());
        // nội dung PDF thật nhưng đuôi .exe, hoặc đuôi ảnh không khớp loại thật
        upload(doc.token(), encounter.getId(), "benh-an.exe", PDF).andExpect(status().isUnsupportedMediaType());
        upload(doc.token(), encounter.getId(), "benh-an.png", PDF).andExpect(status().isUnsupportedMediaType());
        upload(doc.token(), encounter.getId(), "khong-duoi", PDF).andExpect(status().isUnsupportedMediaType());
        // tệp rỗng
        upload(doc.token(), encounter.getId(), "rong.pdf", new byte[0]).andExpect(status().isBadRequest());

        byte[] tooBig = new byte[(int) (10 * 1024 * 1024 + 1)];
        System.arraycopy(PDF, 0, tooBig, 0, PDF.length);
        upload(doc.token(), encounter.getId(), "lon.pdf", tooBig)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));

        assertEquals(before, attachments.findByEncounterId(encounter.getId()).size());
    }

    @Test
    @DisplayName("Tên tệp gốc được làm sạch: không còn đường dẫn/ký tự điều khiển; tệp chỉ nằm trong thư mục upload")
    void sanitizesOriginalFileName() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("att_name");
        IsolatedPatient pat = data.isolatedPatient("att_name");
        Encounter encounter = openEncounter(doc, pat);

        JsonNode traversal = uploadOk(doc.token(), encounter.getId(), "..\\..\\etc/../passwd-evil.pdf", PDF);
        assertEquals("passwd-evil.pdf", traversal.get("originalFileName").asText());

        JsonNode control = uploadOk(doc.token(), encounter.getId(), "a\"b<c>d\r\ne.pdf", PDF);
        String name = control.get("originalFileName").asText();
        assertFalse(name.matches(".*[\"<>\\r\\n].*"), name);

        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        for (JsonNode node : new JsonNode[] {traversal, control}) {
            assertTrue(storedFile(node.get("id").asLong()).startsWith(root));
        }
    }

    @Test
    @DisplayName("Bệnh nhân, bác sĩ khác, Admin không tải lên được (403); lần khám đã hoàn thành → 409")
    void uploadPermissionsAndState() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("att_perm_doc");
        IsolatedDoctor other = data.isolatedDoctor("att_perm_other");
        IsolatedPatient pat = data.isolatedPatient("att_perm_pat");
        Encounter encounter = openEncounter(doc, pat);

        upload(pat.token(), encounter.getId(), "a.pdf", PDF).andExpect(status().isForbidden());
        upload(other.token(), encounter.getId(), "a.pdf", PDF).andExpect(status().isForbidden());
        upload(adminToken(), encounter.getId(), "a.pdf", PDF).andExpect(status().isForbidden());
        upload(doc.token(), 999_999L, "a.pdf", PDF).andExpect(status().isNotFound());
        assertEquals(0, attachments.findByEncounterId(encounter.getId()).size());

        encounter.setStatus("COMPLETED");
        encounters.save(encounter);
        upload(doc.token(), encounter.getId(), "a.pdf", PDF).andExpect(status().isConflict());
        assertEquals(0, attachments.findByEncounterId(encounter.getId()).size());
    }

    // ---------- liệt kê / tải xuống ----------

    @Test
    @DisplayName("Tải xuống: chủ bệnh án/bác sĩ phụ trách nhận đúng nội dung + header an toàn + audit; người khác, Admin → 403")
    void downloadAccessAndHeaders() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("att_dl_doc");
        IsolatedDoctor other = data.isolatedDoctor("att_dl_other");
        IsolatedPatient owner = data.isolatedPatient("att_dl_owner");
        IsolatedPatient stranger = data.isolatedPatient("att_dl_stranger");
        Encounter encounter = openEncounter(doc, owner);
        long id = uploadOk(doc.token(), encounter.getId(), "Kết quả/xét nghiệm máu.pdf", PDF).get("id").asLong();

        MvcResult result = mvc.perform(get("/api/v1/attachments/" + id + "/download")
                .header("Authorization", owner.token()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertArrayEquals(PDF, result.getResponse().getContentAsByteArray());
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("attachment"), disposition);
        assertFalse(disposition.contains("/"), "tên tệp không được chứa dấu gạch chéo: " + disposition);

        mvc.perform(get("/api/v1/attachments/" + id + "/download").header("Authorization", doc.token()))
                .andExpect(status().isOk());
        assertEquals(1, countAudits(AuditActions.ENTITY_ATTACHMENTS, id, AuditActions.ATTACHMENT_DOWNLOAD,
                owner.userId()));
        assertEquals(1, countAudits(AuditActions.ENTITY_ATTACHMENTS, id, AuditActions.ATTACHMENT_DOWNLOAD,
                doc.userId()));

        mvc.perform(get("/api/v1/attachments/" + id + "/download").header("Authorization", stranger.token()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/attachments/" + id + "/download").header("Authorization", other.token()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/attachments/" + id + "/download").header("Authorization", adminToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/attachments/999999/download").header("Authorization", owner.token()))
                .andExpect(status().isNotFound());
        assertEquals(0, countAudits(AuditActions.ENTITY_ATTACHMENTS, id, AuditActions.ATTACHMENT_DOWNLOAD,
                stranger.userId()));
    }

    @Test
    @DisplayName("Liệt kê tệp của lần khám: chủ/bác sĩ phụ trách thấy, người khác 403; không có đường dẫn trong JSON")
    void listAttachments() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("att_list_doc");
        IsolatedPatient owner = data.isolatedPatient("att_list_owner");
        IsolatedPatient stranger = data.isolatedPatient("att_list_stranger");
        Encounter encounter = openEncounter(doc, owner);
        uploadOk(doc.token(), encounter.getId(), "a.pdf", PDF);
        uploadOk(doc.token(), encounter.getId(), "b.png", PNG);

        String url = "/api/v1/encounters/" + encounter.getId() + "/attachments";
        MvcResult result = mvc.perform(get(url).header("Authorization", owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andReturn();
        String json = result.getResponse().getContentAsString();
        assertFalse(json.contains("filePath") || json.contains("storedFileName") || json.contains("uploads"), json);
        mvc.perform(get(url).header("Authorization", doc.token())).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization", stranger.token())).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", adminToken())).andExpect(status().isForbidden());
    }

    // ---------- xóa ----------

    @Test
    @DisplayName("Xóa tệp: bác sĩ phụ trách khi OPEN → 204, xóa cả tệp vật lý, có audit; bệnh nhân/bác sĩ khác 403; đã hoàn thành 409")
    void deleteAttachment() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("att_del_doc");
        IsolatedDoctor other = data.isolatedDoctor("att_del_other");
        IsolatedPatient pat = data.isolatedPatient("att_del_pat");
        Encounter encounter = openEncounter(doc, pat);
        long id = uploadOk(doc.token(), encounter.getId(), "xoa.pdf", PDF).get("id").asLong();
        Path file = storedFile(id);
        assertTrue(Files.exists(file));

        mvc.perform(delete("/api/v1/attachments/" + id).header("Authorization", pat.token()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/attachments/" + id).header("Authorization", other.token()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/attachments/" + id).header("Authorization", adminToken()))
                .andExpect(status().isForbidden());
        assertTrue(Files.exists(file));

        mvc.perform(delete("/api/v1/attachments/" + id).header("Authorization", doc.token()))
                .andExpect(status().isNoContent());
        assertFalse(Files.exists(file), "tệp vật lý phải bị xóa");
        assertFalse(attachments.existsById(id));
        assertEquals(1, countAudits(AuditActions.ENTITY_ATTACHMENTS, id, AuditActions.ATTACHMENT_DELETE, doc.userId()));
        mvc.perform(delete("/api/v1/attachments/" + id).header("Authorization", doc.token()))
                .andExpect(status().isNotFound());

        long kept = uploadOk(doc.token(), encounter.getId(), "giu.pdf", PDF).get("id").asLong();
        encounter.setStatus("COMPLETED");
        encounters.save(encounter);
        mvc.perform(delete("/api/v1/attachments/" + kept).header("Authorization", doc.token()))
                .andExpect(status().isConflict());
        assertTrue(Files.exists(storedFile(kept)));
    }

    @Test
    @DisplayName("Dữ liệu tệp trong DB trỏ ra ngoài thư mục upload (path traversal) → không đọc được, 404")
    void storedPathCannotEscapeUploadDirectory() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("att_escape");
        IsolatedPatient pat = data.isolatedPatient("att_escape");
        Encounter encounter = openEncounter(doc, pat);
        long id = uploadOk(doc.token(), encounter.getId(), "an-toan.pdf", PDF).get("id").asLong();

        // tệp thật tồn tại ngay bên ngoài thư mục "attachments" (cùng thư mục cha của nó)
        Path outside = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("ngoai-thu-muc-" + id + ".pdf");
        try {
            Files.createDirectories(outside.getParent());
            Files.write(outside, PDF);
            var attachment = attachments.findById(id).orElseThrow();
            String download = "/api/v1/attachments/" + id + "/download";

            attachment.setFilePath("../" + outside.getFileName());
            attachments.save(attachment);
            mvc.perform(get(download).header("Authorization", doc.token())).andExpect(status().isNotFound());

            // đường dẫn không hợp lệ về mặt hệ điều hành cũng không được gây lỗi 400/500
            attachment.setFilePath("attachments/\0khong-hop-le.pdf");
            attachments.save(attachment);
            mvc.perform(get(download).header("Authorization", doc.token())).andExpect(status().isNotFound());
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    /** Khi không còn bản ghi nào tham chiếu, thư mục upload chỉ chứa tệp có tên UUID do server sinh. */
    @Test
    @DisplayName("Thư mục upload chỉ chứa tệp tên UUID do server sinh")
    void uploadDirectoryContainsOnlyServerGeneratedNames() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("att_names");
        IsolatedPatient pat = data.isolatedPatient("att_names");
        Encounter encounter = openEncounter(doc, pat);
        uploadOk(doc.token(), encounter.getId(), "../../evil.pdf", PDF);

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("attachments");
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.allMatch(p -> p.getFileName().toString().matches("[0-9a-f-]{36}\\.(pdf|png|jpg)")),
                    "có tệp không đúng quy ước tên");
        }
    }
}
