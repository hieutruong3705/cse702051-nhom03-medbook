package com.phenikaa.cse702051.medbook.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * AC-09.5: khi bật {@code medbook.openapi.enabled}, {@code /v3/api-docs} trả tài liệu OpenAPI hợp lệ liệt kê mọi
 * endpoint {@code /api/v1}. Test này đồng thời xuất tài liệu ra {@code docs/openapi.json} (bản để nộp và đọc ngoại
 * tuyến) và {@code target/openapi.json}.
 */
@TestPropertySource(properties = "medbook.openapi.enabled=true")
class OpenApiExportTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /** Mọi mẫu đường dẫn /api/v1 mà ứng dụng thật sự đăng ký, bỏ phần biểu thức chính quy của biến đường dẫn. */
    private Set<String> registeredPaths() {
        Set<String> paths = new TreeSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().stream()
                .filter(pattern -> pattern.startsWith("/api/v1"))
                .map(pattern -> pattern.replaceAll("\\{(\\w+):[^}]+}", "{$1}"))
                .forEach(paths::add));
        return paths;
    }

    @Test
    @DisplayName("AC-09.5 /v3/api-docs hợp lệ, liệt kê mọi endpoint /api/v1, khai báo xác thực Bearer; xuất docs/openapi.json")
    void exportsOpenApiDocument() throws Exception {
        String raw = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode document = JSON.readTree(raw);

        assertTrue(document.get("openapi").asText().startsWith("3."), "phiên bản OpenAPI 3.x");
        assertEquals("MedBook API", document.get("info").get("title").asText());
        assertEquals("bearer", document.at("/components/securitySchemes/bearerAuth/scheme").asText());

        Set<String> documented = new TreeSet<>();
        document.get("paths").fieldNames().forEachRemaining(documented::add);
        Set<String> registered = registeredPaths();
        assertTrue(registered.size() >= 75, "ứng dụng phải có đủ endpoint, hiện " + registered.size());
        Set<String> missing = new TreeSet<>(registered);
        missing.removeAll(documented);
        assertTrue(missing.isEmpty(), "endpoint chưa có trong tài liệu: " + missing);
        assertTrue(documented.stream().allMatch(path -> path.startsWith("/api/v1")),
                "tài liệu chỉ gồm đường dẫn /api/v1");

        String pretty = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(document) + System.lineSeparator();
        for (Path target : new Path[] { Path.of("docs", "openapi.json"), Path.of("target", "openapi.json") }) {
            Files.createDirectories(target.getParent());
            Files.writeString(target, pretty, StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Khi bật, giao diện Swagger mở được mà không cần đăng nhập")
    void swaggerUiIsReachableWhenEnabled() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
