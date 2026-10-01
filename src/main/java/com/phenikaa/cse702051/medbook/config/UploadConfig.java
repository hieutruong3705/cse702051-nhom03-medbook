package com.phenikaa.cse702051.medbook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.MultipartConfigElement;

/**
 * Giới hạn multipart của container: 10 MB/tệp, 11 MB/yêu cầu. Mặc định của Spring chỉ 1 MB nên nếu không đặt,
 * tệp PDF/ảnh hợp lệ 1–10 MB sẽ bị từ chối. Kiểm tra 10 MB ở {@code AttachmentService} là lớp bảo vệ thứ hai.
 */
@Configuration
public class UploadConfig {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final long MAX_REQUEST_SIZE = 11L * 1024 * 1024;

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement(null, MAX_FILE_SIZE, MAX_REQUEST_SIZE, 0);
    }
}
