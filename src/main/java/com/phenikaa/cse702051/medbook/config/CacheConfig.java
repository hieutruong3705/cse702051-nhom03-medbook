package com.phenikaa.cse702051.medbook.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bộ nhớ đệm trong tiến trình cho danh mục công khai (chuyên khoa, dịch vụ khám): dữ liệu được mọi trang đặt lịch
 * đọc liên tục nhưng rất ít khi thay đổi.
 *
 * <ul>
 * <li>Chỉ đệm trang đầu khi không có từ khóa (xem {@link #FIRST_PAGE_WITHOUT_KEYWORD}), khóa là kích thước trang,
 * nên mỗi vùng đệm có tối đa 100 mục và không thể bị làm phình bằng từ khóa tùy ý.</li>
 * <li>Admin tạo, sửa, xóa danh mục thì vùng đệm tương ứng bị xóa ngay sau khi giao dịch commit; ngoài ra mọi vùng
 * đệm được làm mới định kỳ (xem {@code PublicCatalogCache}) để thay đổi làm trực tiếp trên CSDL cũng hiện ra.</li>
 * <li>Tắt bằng {@code medbook.cache.enabled=false}. Chạy nhiều bản sao thì mỗi bản có vùng đệm riêng và chỉ đồng
 * bộ qua lần làm mới định kỳ.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PUBLIC_SPECIALTIES = "publicSpecialties";
    public static final String PUBLIC_MEDICAL_SERVICES = "publicMedicalServices";

    /** Điều kiện SpEL cho các phương thức {@code listActive(keyword, page, size)}. */
    public static final String FIRST_PAGE_WITHOUT_KEYWORD =
            "(#keyword == null || #keyword.isBlank()) && #page == 0 && #size >= 1 && #size <= 100";

    @Bean
    public CacheManager cacheManager(@Value("${medbook.cache.enabled:true}") boolean enabled) {
        return enabled
                ? new ConcurrentMapCacheManager(PUBLIC_SPECIALTIES, PUBLIC_MEDICAL_SERVICES)
                : new NoOpCacheManager();
    }
}
