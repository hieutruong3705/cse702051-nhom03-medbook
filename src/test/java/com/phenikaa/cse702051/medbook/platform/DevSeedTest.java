package com.phenikaa.cse702051.medbook.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * AC-09.2: chế độ phát triển (H2) nạp {@code data-h2.sql} như trước: đủ tài khoản demo, danh mục, ca và giờ trống
 * mẫu; bộ đếm khóa chính đã được đặt lại nên bản ghi tạo sau không đụng ID cố định của seed.
 */
class DevSeedTest extends AbstractApiTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${spring.sql.init.data-locations}")
    private String dataLocations;

    @Value("${spring.sql.init.platform}")
    private String platform;

    @Value("${spring.flyway.enabled}")
    private boolean flywayEnabled;

    @Test
    @DisplayName("AC-09.2 H2 nạp data-h2.sql: 5 tài khoản demo đúng vai trò, danh mục và ca mẫu có sẵn")
    void h2SeedIsLoaded() {
        assertEquals("classpath:data-h2.sql", dataLocations);
        assertEquals("h2", platform);
        assertTrue(!flywayEnabled, "chế độ phát triển không dùng Flyway");

        assertEquals(List.of("admin1:ADMIN", "doctor1:DOCTOR", "doctor2:DOCTOR", "patient1:PATIENT",
                "patient2:PATIENT"), jdbc.queryForList("""
                        select u.username || ':' || r.code from users u
                        join user_roles ur on ur.user_id = u.id join roles r on r.id = ur.role_id
                        where u.id between 1 and 5 order by u.username
                        """, String.class));
        assertEquals(3, jdbc.queryForObject("select count(*) from roles where id <= 3", Long.class));
        assertEquals(5, jdbc.queryForObject("select count(*) from specialties where id <= 5", Long.class));
        assertEquals(4, jdbc.queryForObject("select count(*) from services where id <= 4", Long.class));
        assertEquals(5, jdbc.queryForObject("select count(*) from medicines where id <= 5", Long.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from doctors where id <= 2", Long.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from patients where id <= 2", Long.class));
        assertEquals(13, jdbc.queryForObject("select count(*) from appointment_slots where id <= 13", Long.class));
    }

    @Test
    @DisplayName("AC-09.2 Ca mẫu có độ dài slot 30 phút cho 4 ngày; bộ đếm khóa chính bắt đầu từ 1000 sau seed")
    void seedSchedulesAndIdentityRestart() {
        Long seededShifts = jdbc.queryForObject("""
                select count(*) from doctor_schedules
                where doctor_id in (1, 2) and slot_minutes = 30
                  and work_date between current_date and dateadd('DAY', 3, current_date)
                """, Long.class);
        assertTrue(seededShifts >= 16, "4 ca mẫu x 4 ngày, hiện có " + seededShifts);

        long userId = data.user("devseed_identity_check", "Iso#Pass123", "PATIENT").getId();
        assertTrue(userId >= 1000, "ID sinh sau seed phải từ 1000, nhận " + userId);
        assertEquals(0, jdbc.queryForObject("select count(*) from users where id between 6 and 999", Long.class));
    }
}
