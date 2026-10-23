package com.phenikaa.cse702051.medbook.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Bản đồ trang và tệp robots cho công cụ tìm kiếm. Cả hai dùng địa chỉ tuyệt đối: lấy từ
 * {@code medbook.public-base-url} (biến môi trường {@code MEDBOOK_PUBLIC_BASE_URL}); để trống thì lấy theo địa chỉ
 * của chính yêu cầu đang tới. Chỉ liệt kê trang công khai; trang sau đăng nhập không có trong bản đồ.
 */
@RestController
public class SeoController {

    /** Trang công khai, theo thứ tự ưu tiên lập chỉ mục. */
    static final List<String> PUBLIC_PAGES = List.of("/", "/doctors", "/specialties", "/services", "/login",
            "/register");

    private final String publicBaseUrl;

    public SeoController(@Value("${medbook.public-base-url:}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public String sitemap(HttpServletRequest request) {
        String base = HtmlUtils.htmlEscape(baseUrl(request));
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String page : PUBLIC_PAGES) {
            xml.append("  <url><loc>").append(base).append(page).append("</loc><changefreq>weekly</changefreq>")
                    .append("<priority>").append("/".equals(page) ? "1.0" : "0.8").append("</priority></url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    @GetMapping(value = "/robots.txt", produces = "text/plain;charset=UTF-8")
    public String robots(HttpServletRequest request) {
        return """
                User-agent: *
                Allow: /
                Disallow: /api/

                Sitemap: %s/sitemap.xml
                """.formatted(baseUrl(request));
    }

    private String baseUrl(HttpServletRequest request) {
        return publicBaseUrl.isEmpty()
                ? ServletUriComponentsBuilder.fromContextPath(request).build().toUriString()
                : publicBaseUrl;
    }
}
