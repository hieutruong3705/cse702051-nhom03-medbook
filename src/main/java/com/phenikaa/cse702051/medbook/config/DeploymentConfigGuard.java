package com.phenikaa.cse702051.medbook.config;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Chặn khởi động ở môi trường triển khai ({@code docker}, {@code prod}) khi khóa ký JWT không an toàn. Thiếu hẳn
 * biến {@code JWT_SECRET} đã làm Spring dừng với thông báo nêu tên biến; lớp này bắt thêm hai trường hợp biến có
 * giá trị nhưng vẫn nguy hiểm: quá ngắn, hoặc là giá trị mẫu đã công khai trong mã nguồn và {@code .env.example}.
 */
@Component
@Profile({ "docker", "prod" })
public class DeploymentConfigGuard implements InitializingBean {

    static final int MIN_SECRET_BYTES = 32;

    /** Các giá trị mẫu xuất hiện trong repository: ai cũng biết nên không được dùng để ký token thật. */
    private static final String[] PUBLIC_SAMPLES = {
            "MedBookSecretKey2024VeryLongSecureKeyForHS256AlgorithmAtLeast256Bits!!",
            "your-super-secret-key-change-in-production-min-32-chars",
            "doi-thanh-chuoi-ngau-nhien-dai-it-nhat-32-ky-tu"
    };

    private final String jwtSecret;

    public DeploymentConfigGuard(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void afterPropertiesSet() {
        validate(jwtSecret);
    }

    public static void validate(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Thiếu biến môi trường JWT_SECRET: phải đặt khóa ký JWT trước khi chạy.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET quá ngắn: cần ít nhất " + MIN_SECRET_BYTES
                    + " byte (khuyến nghị chuỗi ngẫu nhiên 48 ký tự trở lên).");
        }
        for (String sample : PUBLIC_SAMPLES) {
            if (sample.equals(secret.trim())) {
                throw new IllegalStateException("JWT_SECRET đang là giá trị mẫu công khai trong mã nguồn. "
                        + "Hãy sinh một chuỗi ngẫu nhiên riêng cho môi trường này.");
            }
        }
    }
}
