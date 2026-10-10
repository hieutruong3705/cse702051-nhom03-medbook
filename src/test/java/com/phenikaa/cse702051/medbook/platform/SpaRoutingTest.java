package com.phenikaa.cse702051.medbook.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import com.phenikaa.cse702051.medbook.controller.SeoController;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * SV-4: địa chỉ trang của giao diện ({@code /doctors}, {@code /patient/dashboard}) gõ thẳng hay tải lại đều nhận
 * {@code index.html}; API và tệp tĩnh không bao giờ rơi vào đường này; bản đồ trang và robots dùng địa chỉ tuyệt đối.
 */
class SpaRoutingTest extends AbstractApiTest {

    private static final Pattern LOC = Pattern.compile("<loc>([^<]*)</loc>");

    @Test
    @DisplayName("AC-S4.1 Gõ thẳng hoặc tải lại một địa chỉ trang: 200 và được chuyển tiếp tới index.html, không cần token")
    void pageAddressesAreForwardedToTheApp() throws Exception {
        for (String path : List.of("/doctors", "/specialties", "/services", "/login", "/register", "/reset-password",
                "/patient/dashboard", "/receptionist/patients", "/admin/audit-logs", "/account/change-password",
                "/doctor/encounters/15", "/patient/records/7", "/403")) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
        mvc.perform(get("/doctors").param("specialtyId", "1")).andExpect(forwardedUrl("/index.html"));
        mvc.perform(head("/doctors")).andExpect(status().isOk());
        // có dấu / ở cuối vẫn là địa chỉ trang
        mvc.perform(get("/doctors/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/patient/dashboard/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("AC-S4.3 Địa chỉ không tồn tại vẫn nhận index.html để ứng dụng hiện trang 404 của mình")
    void unknownPageAddressesShowTheAppNotFoundPage() throws Exception {
        for (String path : List.of("/abc/xyz", "/khong-co-trang-nay", "/patient/khong-co/1/2")) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    @DisplayName("AC-S4.3 Yêu cầu tới tệp không có trả 404 thật, không trả index.html")
    void missingFilesAreRealNotFound() throws Exception {
        for (String path : List.of("/assets/khong-co.js", "/khong-co.css", "/doctors/anh.png", "/abc/xyz.map")) {
            mvc.perform(get(path).header("Authorization", patientToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(forwardedUrl(null));
        }
        // khách: tệp ngoài danh sách công khai vẫn bị chặn như trước
        mvc.perform(get("/khong-co.css")).andExpect(status().isUnauthorized());
        mvc.perform(get("/assets/khong-co.js")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-S4.2 Không đường /api nào trả về HTML: chưa đăng nhập 401, có token 404 dạng JSON")
    void apiPathsNeverFallBackToTheApp() throws Exception {
        for (String path : List.of("/api", "/api/khong-ton-tai", "/api/v1", "/api/v1/khong-ton-tai",
                "/api/v2/doctors", "/api/v1/khong/ton/tai/sau")) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(forwardedUrl(null))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            mvc.perform(get(path).header("Authorization", patientToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(forwardedUrl(null))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("AC-S4.2 Viết /api dưới dạng mã hóa phần trăm không lách được yêu cầu đăng nhập")
    void percentEncodedApiPathIsStillProtected() throws Exception {
        for (String path : List.of("/%61pi/v1/users", "/%61pi/v1/admin/users", "/%61pi/v1/khong-ton-tai")) {
            int status = mvc.perform(get(URI.create(path))).andReturn().getResponse().getStatus();
            assertTrue(status == 400 || status == 401, path + " phải bị chặn, nhận " + status);
        }
    }

    @Test
    @DisplayName("Chỉ yêu cầu đọc mới nhận trang: POST, PUT, DELETE tới địa chỉ trang vẫn phải đăng nhập")
    void onlyReadsOfPageAddressesArePublic() throws Exception {
        mvc.perform(post("/doctors")).andExpect(status().isUnauthorized());
        mvc.perform(put("/patient/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC-S4.8 Trang được chuyển tiếp vẫn mang đủ năm tiêu đề bảo mật")
    void forwardedPagesKeepSecurityHeaders() throws Exception {
        for (String path : List.of("/doctors", "/patient/dashboard", "/abc/xyz")) {
            mvc.perform(get(path))
                    .andExpect(header().string("Content-Security-Policy", Matchers.containsString("default-src 'self'")))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Permissions-Policy", Matchers.containsString("camera=()")));
        }
    }

    @Test
    @DisplayName("AC-S4.5 /sitemap.xml: mọi <loc> là địa chỉ tuyệt đối, đủ sáu trang công khai, không có trang sau đăng nhập")
    void sitemapListsPublicPagesWithAbsoluteAddresses() throws Exception {
        String xml = mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andReturn().getResponse().getContentAsString();

        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        List<String> locations = LOC.matcher(xml).results().map(match -> match.group(1)).toList();
        assertEquals(List.of("http://localhost/", "http://localhost/doctors", "http://localhost/specialties",
                "http://localhost/services", "http://localhost/login", "http://localhost/register"), locations);
        for (String page : List.of("/patient", "/doctor/", "/admin", "/receptionist", "/api", "#")) {
            assertFalse(xml.contains("localhost" + page), "bản đồ trang không được chứa " + page);
        }
    }

    @Test
    @DisplayName("AC-S4.5 /robots.txt trỏ tới bản đồ trang bằng địa chỉ tuyệt đối và vẫn chặn /api/")
    void robotsPointsToTheAbsoluteSitemap() throws Exception {
        mvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(Matchers.containsString("User-agent: *")))
                .andExpect(content().string(Matchers.containsString("Disallow: /api/")))
                .andExpect(content().string(Matchers.containsString("Sitemap: http://localhost/sitemap.xml")));
    }

    @Test
    @DisplayName("AC-S4.5 Có MEDBOOK_PUBLIC_BASE_URL thì bản đồ trang và robots dùng đúng địa chỉ đó, bỏ dấu / thừa ở cuối")
    void configuredBaseUrlWinsOverTheRequestAddress() {
        SeoController controller = new SeoController(" https://medbook.example/ ");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sitemap.xml");
        request.setServerName("noi-bo.local");

        String xml = controller.sitemap(request);
        Matcher locations = LOC.matcher(xml);
        int count = 0;
        while (locations.find()) {
            assertTrue(locations.group(1).startsWith("https://medbook.example/"), locations.group(1));
            count++;
        }
        assertEquals(6, count);
        assertTrue(xml.contains("<loc>https://medbook.example/doctors</loc>"));
        assertFalse(xml.contains("noi-bo.local"));

        String robots = controller.robots(request);
        assertTrue(robots.contains("Sitemap: https://medbook.example/sitemap.xml"), robots);
        assertTrue(robots.contains("Disallow: /api/"), robots);
    }
}
