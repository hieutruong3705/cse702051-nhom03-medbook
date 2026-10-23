package com.phenikaa.cse702051.medbook.config;

import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.phenikaa.cse702051.medbook.security.PasswordPolicy;

/**
 * Tạo ba tài khoản quản trị ban đầu ({@code admin1}, {@code admin2}, {@code admin3}) cho một CSDL chưa có tài khoản
 * nào, để bản vừa triển khai có người đăng nhập mà không cần nạp dữ liệu demo. Mọi dữ liệu còn lại (danh mục, bác
 * sĩ, lễ tân) do quản trị viên tạo trên giao diện.
 *
 * <ul>
 * <li>Chỉ chạy khi bảng {@code users} còn trống. CSDL đã có tài khoản thì không tạo, không sửa và không đặt lại
 * mật khẩu của ai, kể cả khi biến môi trường đổi giá trị.</li>
 * <li>Mật khẩu ban đầu lấy từ {@code medbook.admin.initial-password} (biến môi trường
 * {@code MEDBOOK_ADMIN_INITIAL_PASSWORD}), không nằm trong mã nguồn. Không đặt thì chỉ ghi cảnh báo; đặt mà không
 * đạt chính sách mật khẩu thì ứng dụng dừng với lý do rõ ràng.</li>
 * <li>Thêm các vai trò còn thiếu trong bảng {@code roles}: migration không tạo vai trò, mà thiếu vai trò thì không
 * ai đăng ký hay được cấp quyền.</li>
 * <li>Chạy ở mọi hồ sơ, kể cả {@code prod}. Khi bật {@code MEDBOOK_SEED_DEMO}, dữ liệu demo được nạp trước nên lớp
 * này không tạo gì thêm.</li>
 * </ul>
 */
@Component
public class InitialAdminRunner implements ApplicationRunner {

    public static final List<String> USERNAMES = List.of("admin1", "admin2", "admin3");

    private static final Logger log = LoggerFactory.getLogger(InitialAdminRunner.class);

    /** Vai trò hệ thống: mã, tên hiển thị, mô tả. Giống dữ liệu demo để hai cách khởi tạo cho cùng kết quả. */
    private static final String[][] ROLES = {
            { "PATIENT", "Bệnh nhân", "Người bệnh đăng ký khám" },
            { "DOCTOR", "Bác sĩ", "Bác sĩ khám chữa bệnh" },
            { "ADMIN", "Quản trị viên", "Quản trị hệ thống" },
            { "RECEPTIONIST", "Lễ tân", "Nhân viên tiếp đón tại quầy" } };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PasswordEncoder passwordEncoder;
    private final String initialPassword;
    private final ObjectProvider<DemoSeedRunner> demoSeed;

    @Autowired
    public InitialAdminRunner(
            DataSource dataSource,
            PasswordEncoder passwordEncoder,
            @Value("${medbook.admin.initial-password:}") String initialPassword,
            ObjectProvider<DemoSeedRunner> demoSeed) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.passwordEncoder = passwordEncoder;
        this.initialPassword = initialPassword == null ? "" : initialPassword;
        this.demoSeed = demoSeed;
    }

    public InitialAdminRunner(DataSource dataSource, PasswordEncoder passwordEncoder, String initialPassword) {
        this(dataSource, passwordEncoder, initialPassword, null);
    }

    @Override
    public void run(ApplicationArguments args) {
        // Dữ liệu demo (nếu bật) chèn vai trò và tài khoản với id tường minh nên phải được nạp trước.
        if (demoSeed != null) {
            demoSeed.ifAvailable(DemoSeedRunner::seedIfEmpty);
        }
        createIfNoUsers();
    }

    /** @return tên đăng nhập của các tài khoản vừa tạo; rỗng khi không tạo gì */
    public List<String> createIfNoUsers() {
        Long users = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        if (users != null && users > 0) {
            return List.of();
        }
        if (initialPassword.isBlank()) {
            log.warn("CSDL chưa có tài khoản nào. Đặt MEDBOOK_ADMIN_INITIAL_PASSWORD rồi khởi động lại để tạo {}.",
                    String.join(", ", USERNAMES));
            return List.of();
        }
        if (!PasswordPolicy.isValid(initialPassword)) {
            throw new IllegalStateException("MEDBOOK_ADMIN_INITIAL_PASSWORD không hợp lệ. " + PasswordPolicy.MESSAGE
                    + ".");
        }
        transaction.executeWithoutResult(status -> create());
        log.info("Đã tạo {} tài khoản quản trị ban đầu: {}. Mỗi người nên đổi mật khẩu sau lần đăng nhập đầu.",
                USERNAMES.size(), String.join(", ", USERNAMES));
        return USERNAMES;
    }

    private void create() {
        LocalDateTime now = LocalDateTime.now();
        for (String[] role : ROLES) {
            Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE code = ?", Long.class, role[0]);
            if (existing == null || existing == 0) {
                jdbc.update("INSERT INTO roles (code, name, description, created_at) VALUES (?, ?, ?, ?)", role[0],
                        role[1], role[2], now);
            }
        }
        Long adminRoleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'ADMIN'", Long.class);
        for (int i = 0; i < USERNAMES.size(); i++) {
            String username = USERNAMES.get(i);
            // Băm riêng từng tài khoản: cùng mật khẩu ban đầu nhưng mỗi dòng một muối khác nhau.
            jdbc.update("INSERT INTO users (username, password_hash, full_name, email, status, failed_login_count, "
                    + "token_version, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', 0, 0, ?, ?)", username,
                    passwordEncoder.encode(initialPassword), "Quản trị viên " + (i + 1), username + "@medbook.local",
                    now, now);
            Long userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
            jdbc.update("INSERT INTO user_roles (user_id, role_id, created_at) VALUES (?, ?, ?)", userId, adminRoleId,
                    now);
        }
    }
}
