package com.phenikaa.cse702051.medbook.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.phenikaa.cse702051.medbook.config.DemoSeedRunner;
import com.phenikaa.cse702051.medbook.config.InitialAdminRunner;

/**
 * Khởi tạo dữ liệu sạch: CSDL vừa chạy xong migration (chưa có vai trò, chưa có tài khoản) nhận đủ bốn vai trò và
 * ba tài khoản quản trị với mật khẩu lấy từ cấu hình; CSDL đã có tài khoản thì không bị đụng tới.
 */
class InitialAdminRunnerTest {

    private static final String PASSWORD = "KhoiTao#2026";
    /** Cost thấp cho test chạy nhanh; ứng dụng dùng bộ băm của {@code SecurityConfig}. */
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private static DataSource migratedDatabase() {
        DataSource dataSource = FlywayMigrationTest.newDatabase();
        FlywayMigrationTest.flyway(dataSource).migrate();
        return dataSource;
    }

    private static long count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    @Test
    @DisplayName("CSDL trống: tạo bốn vai trò và admin1, admin2, admin3 đang hoạt động, đăng nhập được bằng mật khẩu ban đầu")
    void createsRolesAndThreeAdminsInAnEmptyDatabase() {
        DataSource dataSource = migratedDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        List<String> created = new InitialAdminRunner(dataSource, ENCODER, PASSWORD).createIfNoUsers();

        assertEquals(List.of("admin1", "admin2", "admin3"), created);
        assertEquals(List.of("ADMIN", "DOCTOR", "PATIENT", "RECEPTIONIST"),
                jdbc.queryForList("SELECT code FROM roles ORDER BY code", String.class));
        assertEquals(List.of("admin1:Quản trị viên 1:admin1@medbook.local:ACTIVE:ADMIN",
                "admin2:Quản trị viên 2:admin2@medbook.local:ACTIVE:ADMIN",
                "admin3:Quản trị viên 3:admin3@medbook.local:ACTIVE:ADMIN"), jdbc.queryForList(
                        "SELECT CONCAT(u.username, ':', u.full_name, ':', u.email, ':', u.status, ':', r.code) "
                                + "FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id "
                                + "ORDER BY u.username", String.class));
        assertEquals(3, count(jdbc, "users"));
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM users ORDER BY username", String.class);
        hashes.forEach(hash -> assertTrue(ENCODER.matches(PASSWORD, hash), "mật khẩu ban đầu khớp với bản băm"));
        assertEquals(3, hashes.stream().distinct().count(), "mỗi tài khoản một muối riêng");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE failed_login_count <> 0 "
                + "OR token_version <> 0 OR locked_until IS NOT NULL", Long.class));
        for (String table : List.of("patients", "doctors", "specialties", "services", "medicines",
                "appointment_slots", "appointments", "invoices")) {
            assertEquals(0, count(jdbc, table), "dữ liệu sạch: bảng " + table + " không có dòng nào");
        }
    }

    @Test
    @DisplayName("Chạy lại hoặc đổi mật khẩu cấu hình: CSDL đã có tài khoản thì không tạo thêm và không đặt lại mật khẩu")
    void doesNothingOnceAccountsExist() {
        DataSource dataSource = migratedDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        new InitialAdminRunner(dataSource, ENCODER, PASSWORD).createIfNoUsers();
        String hashBefore = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'admin1'",
                String.class);

        assertEquals(List.of(), new InitialAdminRunner(dataSource, ENCODER, "MatKhauKhac#99").createIfNoUsers());

        assertEquals(3, count(jdbc, "users"));
        assertEquals(4, count(jdbc, "roles"));
        assertEquals(hashBefore, jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'admin1'",
                String.class));
    }

    @Test
    @DisplayName("Bảng roles đã có một phần vai trò: chỉ thêm vai trò còn thiếu, giữ nguyên dòng đang có")
    void addsOnlyMissingRoles() {
        DataSource dataSource = migratedDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO roles (id, code, name, created_at) VALUES (7, 'ADMIN', 'Quản trị', NOW())");

        new InitialAdminRunner(dataSource, ENCODER, PASSWORD).createIfNoUsers();

        assertEquals(4, count(jdbc, "roles"));
        assertEquals("7:Quản trị", jdbc.queryForObject("SELECT CONCAT(id, ':', name) FROM roles WHERE code = 'ADMIN'",
                String.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM user_roles WHERE role_id = 7", Long.class));
    }

    @Test
    @DisplayName("Không đặt mật khẩu ban đầu: không tạo gì. Mật khẩu không đạt chính sách: dừng với lý do nêu tên biến")
    void needsAValidInitialPassword() {
        DataSource dataSource = migratedDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertEquals(List.of(), new InitialAdminRunner(dataSource, ENCODER, "").createIfNoUsers());
        assertEquals(List.of(), new InitialAdminRunner(dataSource, ENCODER, null).createIfNoUsers());
        for (String weak : new String[] { "ngan1", "khongcochuso", "12345678" }) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> new InitialAdminRunner(dataSource, ENCODER, weak).createIfNoUsers(), weak);
            assertTrue(error.getMessage().contains("MEDBOOK_ADMIN_INITIAL_PASSWORD"), error.getMessage());
            assertFalse(error.getMessage().contains(weak), "thông báo lỗi không lộ mật khẩu");
        }

        assertEquals(0, count(jdbc, "users"));
        assertEquals(0, count(jdbc, "roles"));
    }

    @Test
    @DisplayName("Bật dữ liệu demo cùng lúc: dữ liệu demo được nạp trước, không tạo thêm admin2, admin3")
    void demoSeedTakesPrecedence() {
        DataSource dataSource = migratedDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        new ApplicationContextRunner()
                .withBean(DataSource.class, () -> dataSource)
                .withBean(PasswordEncoder.class, () -> ENCODER)
                .withUserConfiguration(DemoSeedRunner.class, InitialAdminRunner.class)
                .withPropertyValues("medbook.seed.demo=true", "medbook.admin.initial-password=" + PASSWORD)
                // Kiểm tra ngay trong context: đóng context là đóng luôn kết nối duy nhất của CSDL thử
                .run(context -> {
                    context.getBean(InitialAdminRunner.class).run(null);

                    assertEquals(6, count(jdbc, "users"));
                    assertEquals(0, jdbc.queryForObject(
                            "SELECT COUNT(*) FROM users WHERE username IN ('admin2', 'admin3')", Long.class));
                    String demoHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'admin1'",
                            String.class);
                    assertFalse(ENCODER.matches(PASSWORD, demoHash), "admin1 demo giữ mật khẩu demo");
                });
    }

    @Test
    @DisplayName("Chỉ đặt mật khẩu ban đầu (không bật demo): bean tự lấy mật khẩu từ cấu hình và tạo tài khoản khi chạy")
    void readsThePasswordFromConfiguration() {
        DataSource dataSource = migratedDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        new ApplicationContextRunner()
                .withBean(DataSource.class, () -> dataSource)
                .withBean(PasswordEncoder.class, () -> ENCODER)
                .withUserConfiguration(DemoSeedRunner.class, InitialAdminRunner.class)
                .withPropertyValues("medbook.admin.initial-password=" + PASSWORD)
                .run(context -> {
                    context.getBean(InitialAdminRunner.class).run(null);

                    assertEquals(List.of("admin1", "admin2", "admin3"),
                            jdbc.queryForList("SELECT username FROM users ORDER BY username", String.class));
                    assertTrue(ENCODER.matches(PASSWORD, jdbc.queryForObject(
                            "SELECT password_hash FROM users WHERE username = 'admin2'", String.class)));
                });
    }
}
