package com.phenikaa.cse702051.medbook.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.phenikaa.cse702051.medbook.config.DemoSeedRunner;
import com.phenikaa.cse702051.medbook.config.migration.V2__CxAdditiveSchema;
import com.phenikaa.cse702051.medbook.config.migration.V3__ReceptionistRole;
import com.phenikaa.cse702051.medbook.config.migration.V4__SlotHolds;

import jakarta.persistence.Table;

/**
 * AC-09.1, 09.8: toàn bộ migration Flyway chạy được trên H2 ở chế độ MySQL (không lỗi cú pháp), trên cả CSDL mới
 * lẫn CSDL đã có sẵn một phần thay đổi; dữ liệu demo chỉ được nạp khi có cờ, đúng một lần và không bao giờ ở prod.
 * Việc kiểm tra với MySQL thật (Hibernate {@code validate}) do {@code scripts/verify-mysql-schema.ps1} đảm nhận.
 */
class FlywayMigrationTest {

    /** Mọi bảng mà các entity JPA ánh xạ tới, đọc từ annotation để test tự phát hiện entity mới thiếu migration. */
    private static Set<String> entityTables() throws Exception {
        Set<String> tables = new TreeSet<>();
        var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(
                jakarta.persistence.Entity.class));
        for (var candidate : scanner.findCandidateComponents("com.phenikaa.cse702051.medbook.model")) {
            Table table = Class.forName(candidate.getBeanClassName()).getAnnotation(Table.class);
            assertTrue(table != null && !table.name().isBlank(), candidate.getBeanClassName() + " thiếu @Table(name)");
            tables.add(table.name().toLowerCase(Locale.ROOT));
        }
        return tables;
    }

    /**
     * CSDL H2 mới ở chế độ MySQL. Dùng MỘT kết nối cho cả test: H2 gắn biểu thức CHECK với phiên đã tạo ra nó, nên
     * nếu Flyway đóng kết nối của mình thì CHECK so sánh chuỗi sẽ báo lỗi "database has been closed" ở phiên sau.
     */
    private static DataSource newDatabase() {
        return new SingleConnectionDataSource("jdbc:h2:mem:flyway_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1", "sa", "",
                true);
    }

    private static Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .javaMigrations(new V2__CxAdditiveSchema(), new V3__ReceptionistRole(), new V4__SlotHolds())
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
    }

    private static Set<String> tables(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT LOWER(table_name) FROM information_schema.tables "
                + "WHERE LOWER(table_schema) = 'public'", String.class).stream()
                .filter(name -> !name.startsWith("flyway_"))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> columns(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("SELECT LOWER(column_name) FROM information_schema.columns "
                + "WHERE LOWER(table_schema) = 'public' AND LOWER(table_name) = ?", String.class, table).stream()
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> indexes(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("SELECT LOWER(index_name) FROM information_schema.indexes "
                + "WHERE LOWER(table_schema) = 'public' AND LOWER(table_name) = ?", String.class, table).stream()
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    @DisplayName("AC-09.8 CSDL mới: V1 đến V4 chạy hết, đủ bảng cho mọi entity, đủ cột và chỉ mục mới; chạy lại không đổi gì")
    void migratesAnEmptyDatabase() throws Exception {
        DataSource dataSource = newDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        MigrateResult result = flyway(dataSource).migrate();

        assertEquals(4, result.migrationsExecuted);
        assertEquals("4", result.targetSchemaVersion);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM roles", Long.class),
                "V3 không chèn vào bảng roles rỗng: vai trò đến từ dữ liệu khởi tạo, có id tường minh");
        assertEquals(entityTables(), tables(jdbc), "mỗi entity phải có bảng do migration tạo, và ngược lại");
        assertTrue(columns(jdbc, "prescription_items").contains("medicine_id"));
        assertTrue(columns(jdbc, "invoices").containsAll(List.of("voided_at", "void_reason")));
        assertTrue(columns(jdbc, "doctor_schedules").contains("slot_minutes"));
        assertTrue(indexes(jdbc, "appointments").containsAll(
                List.of("idx_appointments_patient", "idx_appointments_doctor_status")));
        assertTrue(indexes(jdbc, "invoices").containsAll(List.of("idx_invoices_patient_status", "idx_invoices_issued")));
        assertTrue(indexes(jdbc, "audit_logs").containsAll(
                List.of("idx_audit_logs_created", "idx_audit_logs_action_created")));
        assertTrue(indexes(jdbc, "doctors").contains("uk_doctors_license"));
        assertEquals(Set.of("id", "slot_id", "user_id", "expires_at", "created_at"), columns(jdbc, "slot_holds"));
        // UNIQUE(slot_id) được kiểm bằng hành vi ở test riêng: H2 tự đặt tên chỉ mục của ràng buộc khai trong CREATE TABLE
        assertTrue(indexes(jdbc, "slot_holds").containsAll(List.of("idx_slot_holds_expires", "idx_slot_holds_user")));

        assertEquals(0, flyway(dataSource).migrate().migrationsExecuted, "chạy lại không còn gì để làm");
    }

    @Test
    @DisplayName("Ràng buộc ở mức CSDL chặn dữ liệu vi phạm bất biến dù mã ứng dụng sai (sổ tay Buổi 6)")
    void databaseConstraintsRejectInvalidRows() {
        DataSource dataSource = newDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(dataSource).migrate();
        new DemoSeedRunner(dataSource, new ClassPathResource("db/seed/demo-seed.sql"), false).seedIfEmpty();

        String[] violations = {
                // giờ kết thúc không sau giờ bắt đầu
                "INSERT INTO appointment_slots (doctor_id, slot_date, start_time, end_time, is_available, status, "
                        + "version, created_at, updated_at) VALUES (1, CURDATE(), '10:00:00', '09:00:00', TRUE, "
                        + "'AVAILABLE', 0, NOW(), NOW())",
                // trạng thái slot ngoài tập cho phép
                "UPDATE appointment_slots SET status = 'XYZ' WHERE doctor_id = 1",
                // giá âm, chiết khấu lớn hơn tổng, số lượng bằng 0
                "UPDATE services SET price = -1 WHERE id = 1",
                "INSERT INTO invoices (invoice_code, patient_id, subtotal, discount_amount, total_amount, status, "
                        + "created_at, updated_at) VALUES ('X-1', 1, 100, 200, 0, 'UNPAID', NOW(), NOW())",
                "INSERT INTO invoices (invoice_code, patient_id, subtotal, discount_amount, total_amount, status, "
                        + "created_at, updated_at) VALUES ('X-2', 1, 100, 0, 100, 'DONE', NOW(), NOW())",
                // hai ngày nghỉ trùng nhau của một bác sĩ
                "INSERT INTO doctor_day_offs (doctor_id, off_date, created_at) VALUES (1, '2030-01-01', NOW()), "
                        + "(1, '2030-01-01', NOW())",
                // độ dài slot ngoài khoảng 5-120
                "UPDATE doctor_schedules SET slot_minutes = 500 WHERE doctor_id = 1",
                // trùng mã, trùng tên đăng nhập, trùng số giấy phép
                "UPDATE users SET username = 'admin1' WHERE id = 2",
                "UPDATE doctors SET license_number = 'CCHN-0001' WHERE id = 2",
                // khóa ngoại: slot của bác sĩ không tồn tại
                "INSERT INTO appointment_slots (doctor_id, slot_date, start_time, end_time, is_available, status, "
                        + "version, created_at, updated_at) VALUES (999999, CURDATE(), '08:00:00', '09:00:00', TRUE, "
                        + "'AVAILABLE', 0, NOW(), NOW())" };
        for (String sql : violations) {
            assertThrows(DataAccessException.class, () -> jdbc.update(sql), sql);
        }
    }

    @Test
    @DisplayName("AC-09.4 CSDL đã có (chưa có lịch sử Flyway, đã có sẵn một phần thay đổi): baseline V1 rồi chỉ chạy V2, V3 và V4, không lỗi")
    void upgradesAnExistingDatabaseWithoutHistory() throws Exception {
        DataSource dataSource = newDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // Dựng lại trạng thái CSDL nghiệm thu: lược đồ V1 nhập tay, không có bảng flyway_schema_history...
        String baseline = new String(new ClassPathResource("db/migration/V1__baseline.sql").getInputStream()
                .readAllBytes(), StandardCharsets.UTF_8);
        new ResourceDatabasePopulator(new ByteArrayResource(baseline.getBytes(StandardCharsets.UTF_8)))
                .execute(dataSource);
        // ...và, như volume Docker từng chạy ddl-auto=update, đã có sẵn một phần thay đổi của V2
        jdbc.execute("ALTER TABLE invoices ADD COLUMN voided_at DATETIME(6) DEFAULT NULL");
        jdbc.execute("CREATE TABLE notifications (id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, "
                + "type VARCHAR(40) NOT NULL, title VARCHAR(150) NOT NULL, message VARCHAR(500) NOT NULL, "
                + "appointment_id BIGINT DEFAULT NULL, created_at DATETIME(6) NOT NULL, "
                + "read_at DATETIME(6) DEFAULT NULL, PRIMARY KEY (id))");
        jdbc.execute("CREATE INDEX some_old_name ON appointments (patient_id)");
        jdbc.update("INSERT INTO roles (id, code, name, created_at) VALUES (1, 'PATIENT', 'Bệnh nhân', NOW())");

        MigrateResult result = flyway(dataSource).migrate();

        assertEquals(3, result.migrationsExecuted, "chỉ V2, V3 và V4 được chạy, V1 được coi là đã có");
        assertEquals(entityTables(), tables(jdbc));
        assertTrue(columns(jdbc, "invoices").containsAll(List.of("voided_at", "void_reason")));
        assertFalse(indexes(jdbc, "appointments").contains("idx_appointments_patient"),
                "đã có chỉ mục khác trên đúng cột này thì không tạo thêm");
        assertEquals(List.of("1:PATIENT"), jdbc.queryForList(
                "SELECT CONCAT(id, ':', code) FROM roles WHERE code <> 'RECEPTIONIST'", String.class),
                "dữ liệu cũ còn nguyên");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE code = 'RECEPTIONIST' "
                + "AND name = 'Lễ tân'", Long.class), "CSDL đã có vai trò thì V3 thêm vai trò Lễ tân");
    }

    @Test
    @DisplayName("AC-09.1 Dữ liệu demo: có cờ thì nạp đúng một lần (6 tài khoản, 4 vai trò, giờ trống 7 ngày tới); chạy lại không nạp chồng")
    void demoSeedLoadsOnceWhenEnabled() {
        DataSource dataSource = newDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(dataSource).migrate();
        DemoSeedRunner runner = new DemoSeedRunner(dataSource, new ClassPathResource("db/seed/demo-seed.sql"), false);

        assertTrue(runner.seedIfEmpty());

        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM roles", Long.class));
        assertEquals(List.of("ADMIN", "DOCTOR", "DOCTOR", "PATIENT", "PATIENT", "RECEPTIONIST"), jdbc.queryForList(
                "SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id ORDER BY r.code", String.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM doctors", Long.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM patients", Long.class));
        assertEquals(28, jdbc.queryForObject("SELECT COUNT(*) FROM doctor_schedules", Long.class));
        assertEquals(84, jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slots", Long.class));
        assertEquals(84, jdbc.queryForObject("SELECT COUNT(*) FROM appointment_slots s JOIN doctor_schedules d "
                + "ON d.doctor_id = s.doctor_id AND d.work_date = s.slot_date AND s.start_time >= d.start_time "
                + "AND s.end_time <= d.end_time WHERE s.slot_date > CURDATE()", Long.class),
                "mọi giờ trống demo nằm trong một ca và ở tương lai");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM services sv WHERE NOT EXISTS (SELECT 1 "
                + "FROM appointment_slots s WHERE TIMESTAMPDIFF(MINUTE, s.start_time, s.end_time) "
                + ">= sv.duration_minutes)", Long.class),
                "AC-S2.5: dịch vụ demo nào cũng có ít nhất một khung giờ đủ dài để đặt");
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE password_hash LIKE '$2a$12$%'",
                Long.class), "mật khẩu demo được lưu dạng băm BCrypt cost 12");

        assertFalse(runner.seedIfEmpty(), "đã có tài khoản thì không nạp lại");
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class));
    }

    @Test
    @DisplayName("AC-09.1 Không có cờ thì không có dữ liệu mẫu; ở hồ sơ prod thì dù có cờ cũng không nạp")
    void demoSeedIsSkippedWithoutFlagOrInProduction() {
        DataSource dataSource = newDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(dataSource).migrate();

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class), "migration không tạo dữ liệu");
        DemoSeedRunner inProduction = new DemoSeedRunner(dataSource, new ClassPathResource("db/seed/demo-seed.sql"),
                true);
        assertFalse(inProduction.seedIfEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class));

        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(DataSource.class, () -> dataSource)
                .withUserConfiguration(DemoSeedRunner.class)
                .run(context -> assertFalse(context.containsBean("demoSeedRunner"), "không đặt cờ thì không có bean"));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(DataSource.class, () -> dataSource)
                .withUserConfiguration(DemoSeedRunner.class)
                .withPropertyValues("medbook.seed.demo=true")
                .run(context -> assertTrue(context.containsBean("demoSeedRunner")));
    }

    @Test
    @DisplayName("AC-S1.10 V4 chạy lại nhiều lần không lỗi và không đụng dữ liệu; slot_holds chỉ nhận một bản giữ cho mỗi khung giờ, gỡ khung giờ thì bản giữ đi theo")
    void slotHoldsMigrationIsRepeatableAndEnforcesOneHoldPerSlot() throws Exception {
        DataSource dataSource = newDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(dataSource).migrate();
        new DemoSeedRunner(dataSource, new ClassPathResource("db/seed/demo-seed.sql"), false).seedIfEmpty();
        Long slotId = jdbc.queryForObject("SELECT MIN(id) FROM appointment_slots", Long.class);
        jdbc.update("INSERT INTO slot_holds (slot_id, user_id, expires_at, created_at) VALUES (?, 4, NOW(), NOW())",
                slotId);

        for (int i = 0; i < 2; i++) {
            try (java.sql.Connection connection = dataSource.getConnection()) {
                new V4__SlotHolds().migrate(new org.flywaydb.core.api.migration.Context() {
                    @Override
                    public org.flywaydb.core.api.configuration.Configuration getConfiguration() {
                        return null;
                    }

                    @Override
                    public java.sql.Connection getConnection() {
                        return connection;
                    }
                });
            }
        }

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM slot_holds", Long.class), "dữ liệu còn nguyên");
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO slot_holds (slot_id, user_id, expires_at, created_at) VALUES (?, 5, NOW(), NOW())", slotId),
                "UNIQUE(slot_id): một khung giờ, một bản giữ");
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO slot_holds (slot_id, user_id, expires_at, created_at) VALUES (999999, 4, NOW(), NOW())"),
                "khóa ngoại tới khung giờ");
        jdbc.update("DELETE FROM appointment_slots WHERE id = ?", slotId);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM slot_holds", Long.class),
                "ON DELETE CASCADE: gỡ khung giờ thì bản giữ đi theo");
    }

    @Test
    @DisplayName("V3 chạy lại nhiều lần không tạo vai trò trùng; CSDL đã có vai trò Lễ tân thì giữ nguyên")
    void receptionistRoleMigrationIsRepeatable() throws Exception {
        DataSource dataSource = newDatabase();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(dataSource).migrate();
        new DemoSeedRunner(dataSource, new ClassPathResource("db/seed/demo-seed.sql"), false).seedIfEmpty();
        Long seededId = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'RECEPTIONIST'", Long.class);

        for (int i = 0; i < 2; i++) {
            try (java.sql.Connection connection = dataSource.getConnection()) {
                new V3__ReceptionistRole().migrate(new org.flywaydb.core.api.migration.Context() {
                    @Override
                    public org.flywaydb.core.api.configuration.Configuration getConfiguration() {
                        return null;
                    }

                    @Override
                    public java.sql.Connection getConnection() {
                        return connection;
                    }
                });
            }
        }

        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM roles", Long.class));
        assertEquals(seededId, jdbc.queryForObject("SELECT id FROM roles WHERE code = 'RECEPTIONIST'", Long.class));
    }
}
