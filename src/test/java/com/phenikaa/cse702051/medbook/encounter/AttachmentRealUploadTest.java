package com.phenikaa.cse702051.medbook.encounter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.support.ApiTestData;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * BE-04 (mục 7) trên Tomcat THẬT (không phải MockMvc): MockMvc bỏ qua giới hạn multipart của container nên không
 * phát hiện được việc tệp 1–10 MB hợp lệ bị từ chối (mặc định Spring chỉ cho 1 MB) hay tệp > 10 MB không trả 413.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttachmentRealUploadTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private ApiTestData data;

    @Value("${medbook.upload-dir}")
    private String uploadDir;

    private HttpResponse<String> upload(String token, long encounterId, byte[] content) throws Exception {
        String boundary = "----medbook" + UUID.randomUUID();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"lon.pdf\""
                + "\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/encounters/" + encounterId + "/attachments"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, content, tail)))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] pdf(int size) {
        byte[] bytes = new byte[size];
        byte[] magic = "%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(magic, 0, bytes, 0, magic.length);
        return bytes;
    }

    @Test
    @DisplayName("Tomcat thật: tệp 5 MB hợp lệ được nhận (201) và tải lại đúng nội dung; tệp > 10 MB → 413")
    void largeFilesAreHandledByTheRealContainer() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("real_upload");
        IsolatedPatient pat = data.isolatedPatient("real_upload");
        Encounter encounter = data.openEncounter(doc.doctorId(), pat.patientId());

        byte[] fiveMb = pdf(5 * 1024 * 1024);
        HttpResponse<String> ok = upload(doc.token(), encounter.getId(), fiveMb);
        assertEquals(201, ok.statusCode(), ok.body());
        long id = JSON.readTree(ok.body()).get("id").asLong();

        HttpRequest download = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/v1/attachments/" + id + "/download")).header("Authorization", pat.token())
                .timeout(Duration.ofSeconds(60)).GET().build();
        HttpResponse<byte[]> downloaded = HttpClient.newHttpClient().send(download,
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, downloaded.statusCode());
        assertArrayEquals(fiveMb, downloaded.body());
        assertEquals("nosniff", downloaded.headers().firstValue("X-Content-Type-Options").orElse(""));

        HttpResponse<String> tooBig = upload(doc.token(), encounter.getId(), pdf(10 * 1024 * 1024 + 1024));
        assertEquals(413, tooBig.statusCode(), tooBig.body());
        assertTrue(tooBig.body().contains("PAYLOAD_TOO_LARGE"), tooBig.body());
    }
}
