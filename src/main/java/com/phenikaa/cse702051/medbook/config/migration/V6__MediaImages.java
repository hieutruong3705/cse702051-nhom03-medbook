package com.phenikaa.cse702051.medbook.config.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * V6: ảnh công khai do người dùng tải lên (ảnh đại diện bác sĩ, ảnh chuyên khoa).
 *
 * <ul>
 * <li>Bảng mới {@code media_images}: dữ liệu ảnh (MEDIUMBLOB), loại ảnh và dung lượng. Ảnh lưu trong CSDL để triển
 * khai lại container không làm mất ảnh.</li>
 * <li>{@code doctors.avatar_image_id}, {@code specialties.image_id}: id ảnh, cho phép NULL (chưa có ảnh thì giao diện
 * dùng ảnh minh họa hoặc biểu tượng như trước).</li>
 * </ul>
 *
 * <p><b>Chỉ cộng thêm và chạy lại được.</b> Bản SQL tương đương để chạy tay: {@code scripts/media-images-schema.mysql.sql}.
 */
@Component
public class V6__MediaImages extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V6__MediaImages.class);

    static final String TABLE = "media_images";

    static final Map<String, String> DOCTOR_COLUMNS = new LinkedHashMap<>();
    static final Map<String, String> SPECIALTY_COLUMNS = new LinkedHashMap<>();

    static {
        DOCTOR_COLUMNS.put("avatar_image_id", "BIGINT DEFAULT NULL");
        SPECIALTY_COLUMNS.put("image_id", "BIGINT DEFAULT NULL");
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            addMissingColumns(connection, statement, "doctors", DOCTOR_COLUMNS);
            addMissingColumns(connection, statement, "specialties", SPECIALTY_COLUMNS);
            if (tableExists(connection, TABLE)) {
                log.info("Flyway V6: bảng {} đã có, bỏ qua.", TABLE);
                return;
            }
            statement.execute("""
                    CREATE TABLE media_images (
                      id BIGINT NOT NULL AUTO_INCREMENT,
                      content_type VARCHAR(30) NOT NULL,
                      size_bytes INT NOT NULL,
                      data MEDIUMBLOB NOT NULL,
                      created_at DATETIME(6) NOT NULL,
                      PRIMARY KEY (id),
                      CONSTRAINT chk_media_images_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
                      CONSTRAINT chk_media_images_size CHECK (size_bytes > 0 AND size_bytes <= 1048576)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""");
        }
        log.info("Flyway V6: đã tạo bảng {}.", TABLE);
    }

    private static void addMissingColumns(Connection connection, Statement statement, String table,
            Map<String, String> columns) throws SQLException {
        for (Map.Entry<String, String> column : columns.entrySet()) {
            if (columnExists(connection, table, column.getKey())) {
                continue;
            }
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column.getKey() + " " + column.getValue());
            log.info("Flyway V6: đã thêm cột {}.{}.", table, column.getKey());
        }
    }

    /** So tên không phân biệt hoa thường: MySQL trên Linux lưu đúng kiểu chữ, H2 có thể lưu chữ hoa. */
    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(),
                null, new String[] { "TABLE" })) {
            while (tables.next()) {
                if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), connection.getSchema(),
                null, null)) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
