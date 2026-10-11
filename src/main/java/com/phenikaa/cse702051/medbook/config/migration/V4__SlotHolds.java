package com.phenikaa.cse702051.medbook.config.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * V4: bảng {@code slot_holds} cho việc giữ chỗ có thời hạn trên khung giờ khám.
 *
 * <p><b>Chỉ cộng thêm và chạy lại được:</b> tạo đúng một bảng mới, chỉ khi chưa có; không đụng bảng nào đang có.
 * UNIQUE trên {@code slot_id} bảo đảm một khung giờ có tối đa một bản giữ. Hai khóa ngoại dùng
 * {@code ON DELETE CASCADE}: gỡ một khung giờ hoặc một tài khoản thì bản giữ đi theo, không chặn thao tác đó.
 *
 * <p>Bản SQL tương đương để chạy tay trên CSDL không dùng Flyway nằm ở {@code scripts/slot-holds-schema.mysql.sql}.
 */
@Component
public class V4__SlotHolds extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V4__SlotHolds.class);

    static final String TABLE = "slot_holds";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (tableExists(connection)) {
            log.info("Flyway V4: bảng {} đã có, bỏ qua.", TABLE);
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE slot_holds (
                      id BIGINT NOT NULL AUTO_INCREMENT,
                      slot_id BIGINT NOT NULL,
                      user_id BIGINT NOT NULL,
                      expires_at DATETIME(6) NOT NULL,
                      created_at DATETIME(6) NOT NULL,
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_slot_holds_slot (slot_id),
                      KEY idx_slot_holds_expires (expires_at),
                      KEY idx_slot_holds_user (user_id),
                      CONSTRAINT fk_slot_holds_slot FOREIGN KEY (slot_id) REFERENCES appointment_slots (id)
                        ON DELETE CASCADE,
                      CONSTRAINT fk_slot_holds_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""");
        }
        log.info("Flyway V4: đã tạo bảng {}.", TABLE);
    }

    /** So tên không phân biệt hoa thường: MySQL trên Linux lưu đúng kiểu chữ, H2 có thể lưu chữ hoa. */
    private static boolean tableExists(Connection connection) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(),
                null, new String[] { "TABLE" })) {
            while (tables.next()) {
                if (TABLE.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
