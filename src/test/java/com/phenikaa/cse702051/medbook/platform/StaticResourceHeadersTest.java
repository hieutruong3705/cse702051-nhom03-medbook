package com.phenikaa.cse702051.medbook.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * Thời hạn lưu đệm của giao diện tĩnh. Tệp có mã băm trong {@code /assets} được giữ một năm, trang gốc luôn
 * được kiểm lại, phản hồi API không đổi. Việc nén (tầng máy chủ web, MockMvc không đi qua) được kiểm trên ứng dụng
 * chạy thật; kết quả ghi ở tài liệu hiệu năng.
 */
class StaticResourceHeadersTest extends AbstractApiTest {

    private static final String ONE_YEAR = "max-age=31536000, public, immutable";

    /** Một tệp có thật trong bản dựng giao diện, để test không phụ thuộc mã băm của từng lần dựng. */
    private static String assetPath(String extension) throws Exception {
        Resource[] assets = new PathMatchingResourcePatternResolver().getResources("classpath:/static/assets/*");
        return Arrays.stream(assets)
                .map(Resource::getFilename)
                .filter(name -> name != null && name.endsWith(extension))
                .sorted()
                .findFirst()
                .map(name -> "/assets/" + name)
                .orElseThrow(() -> new IllegalStateException("bản dựng giao diện thiếu tệp " + extension));
    }

    @Test
    @DisplayName("Tệp JS và CSS trong /assets: lưu đệm công khai một năm, immutable; không còn Pragma hay Expires")
    void hashedAssetsAreCachedForAYear() throws Exception {
        for (String path : new String[] { assetPath(".js"), assetPath(".css"), assetPath(".woff2") }) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", ONE_YEAR))
                    .andExpect(header().doesNotExist("Pragma"))
                    .andExpect(header().doesNotExist("Expires"));
        }
    }

    @Test
    @DisplayName("Trang gốc: / chuyển tới index.html, và index.html trả no-cache để luôn được kiểm lại")
    void rootPageIsAlwaysRevalidated() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().exists("Last-Modified"));
    }

    @Test
    @DisplayName("Trang gốc chưa đổi thì trả 304 khi trình duyệt kiểm lại bằng If-Modified-Since")
    void unchangedRootPageAnswersNotModified() throws Exception {
        String lastModified = mvc.perform(get("/index.html")).andReturn().getResponse().getHeader("Last-Modified");
        assertTrue(lastModified != null && !lastModified.isBlank());

        mvc.perform(get("/index.html").header("If-Modified-Since", lastModified))
                .andExpect(status().isNotModified())
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    @DisplayName("Tệp tĩnh và trang gốc vẫn mang đủ tiêu đề bảo mật")
    void staticResponsesKeepSecurityHeaders() throws Exception {
        for (String path : new String[] { assetPath(".js"), "/index.html" }) {
            mvc.perform(get(path))
                    .andExpect(header().string("Content-Security-Policy", Matchers.containsString("default-src 'self'")))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Permissions-Policy", Matchers.containsString("camera=()")));
        }
    }

    @Test
    @DisplayName("Phản hồi API không đổi: vẫn no-store; tệp không có trong /assets trả 404 và không được lưu đệm lâu dài")
    void apiResponsesAndMissingAssetsAreNotCached() throws Exception {
        mvc.perform(get("/api/v1/doctors"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
        mvc.perform(get("/api/v1/users/me").header("Authorization", patientToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
        mvc.perform(get("/assets/khong-co-tep-nay.js"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", Matchers.not(Matchers.containsString("immutable"))));
    }
}
