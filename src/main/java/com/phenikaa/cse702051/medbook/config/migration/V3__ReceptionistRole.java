package com.phenikaa.cse702051.medbook.config.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * V3: thêm vai trò Lễ tân ({@code RECEPTIONIST}) vào bảng {@code roles}. Chỉ thêm DỮ LIỆU, không đổi lược đồ.
 *
 * <p><b>Chỉ chèn khi bảng {@code roles} đã có dữ liệu mà chưa có vai trò này.</b> Trên CSDL vừa dựng, bảng còn
 * rỗng và dữ liệu demo ({@code db/seed/demo-seed.sql}, nạp SAU Flyway) chèn các vai trò với {@code id} tường minh
 * 1–4; nếu migration này chèn trước, dòng mới sẽ chiếm {@code id} 1 và tệp seed lỗi khóa chính. CSDL mới vì thế
 * nhận vai trò Lễ tân từ tệp seed.
 *
 * <p>Chạy lại được: đã có vai trò thì không làm gì. Bản SQL tương đương để chạy tay trên CSDL không dùng Flyway
 * nằm ở {@code scripts/receptionist-role.mysql.sql}.
 */
@Component
public class V3__ReceptionistRole extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V3__ReceptionistRole.class);

    static final String CODE = "RECEPTIONIST";
    static final String NAME = "Lễ tân";
    static final String DESCRIPTION = "Nhân viên tiếp đón tại quầy";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (count(connection, "SELECT COUNT(*) FROM roles") == 0) {
            log.info("Flyway V3: bảng roles còn rỗng, vai trò {} sẽ đến từ dữ liệu khởi tạo.", CODE);
            return;
        }
        if (count(connection, "SELECT COUNT(*) FROM roles WHERE code = '" + CODE + "'") > 0) {
            log.info("Flyway V3: vai trò {} đã có, không chèn thêm.", CODE);
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO roles (code, name, description, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
            insert.setString(1, CODE);
            insert.setString(2, NAME);
            insert.setString(3, DESCRIPTION);
            insert.executeUpdate();
        }
        log.info("Flyway V3: đã thêm vai trò {}.", CODE);
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
