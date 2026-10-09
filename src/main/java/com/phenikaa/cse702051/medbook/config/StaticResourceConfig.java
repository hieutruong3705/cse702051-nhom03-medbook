package com.phenikaa.cse702051.medbook.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Thời hạn lưu đệm phía trình duyệt cho giao diện tĩnh.
 *
 * <ul>
 * <li>{@code /assets/**}: tên tệp do Vite sinh có kèm mã băm của nội dung, nên nội dung của một địa chỉ không bao giờ
 * đổi. Trình duyệt được giữ một năm và không cần hỏi lại ({@code immutable}).</li>
 * <li>{@code /index.html} (cũng là trang gốc {@code /}): tên không đổi giữa các bản phát hành và là nơi trỏ tới các
 * tệp trong {@code /assets}, nên trình duyệt được giữ bản sao nhưng phải kiểm lại với máy chủ trước mỗi lần dùng
 * ({@code no-cache}); chưa đổi thì máy chủ trả 304, không gửi lại nội dung.</li>
 * </ul>
 *
 * Không dùng {@code spring.web.resources.cache.*} vì thuộc tính đó áp cùng một thời hạn cho mọi tệp tĩnh, kể cả
 * trang gốc. Phản hồi API không đi qua đây và giữ nguyên {@code no-store}.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final String STATIC_ROOT = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC_ROOT + "assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        registry.addResourceHandler("/index.html")
                .addResourceLocations(STATIC_ROOT)
                .setCacheControl(CacheControl.noCache());
    }
}
