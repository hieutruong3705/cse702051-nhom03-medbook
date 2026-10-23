package com.phenikaa.cse702051.medbook.config;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Nguồn duy nhất của khóa ký JWT. Khóa lấy từ biến môi trường {@code JWT_SECRET}; mã nguồn không chứa khóa mặc định.
 *
 * <p>Ở máy phát triển (không đặt {@code JWT_SECRET}) khóa được sinh ngẫu nhiên mỗi lần khởi động, nên mọi phiên đăng
 * nhập mất hiệu lực khi ứng dụng khởi động lại. Các hồ sơ triển khai ({@code docker}, {@code prod}, {@code online})
 * khai báo {@code jwt.secret: ${JWT_SECRET}} không có giá trị thay thế, nên thiếu biến là ứng dụng không khởi động.</p>
 */
@Component
public class JwtSecretProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretProvider.class);
    private static final int GENERATED_BYTES = 48;

    private final String value;

    public JwtSecretProvider(@Value("${jwt.secret:}") String configured) {
        if (configured == null || configured.isBlank()) {
            byte[] random = new byte[GENERATED_BYTES];
            new SecureRandom().nextBytes(random);
            this.value = Base64.getEncoder().encodeToString(random);
            log.warn("Chưa đặt JWT_SECRET: dùng khóa ký sinh ngẫu nhiên cho lần chạy này. "
                    + "Chỉ phù hợp máy phát triển; các phiên đăng nhập sẽ mất hiệu lực khi khởi động lại.");
        } else {
            this.value = configured;
        }
    }

    public String value() {
        return value;
    }
}
