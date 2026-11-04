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
 * V5: đánh giá lần khám và hồ sơ mở rộng (góp ý của giảng viên ngày 04/11/2026).
 *
 * <ul>
 * <li>Bảng mới {@code doctor_reviews}: mỗi lịch hẹn đã hoàn thành có tối đa một đánh giá
 * ({@code UNIQUE(appointment_id)}), điểm 1–5 và trạng thái VISIBLE/HIDDEN được nhắc lại bằng CHECK.</li>
 * <li>{@code doctors}: thêm học hàm/học vị, số năm kinh nghiệm, quá trình đào tạo, thế mạnh chuyên môn và quá
 * trình công tác để hiển thị trên trang công khai.</li>
 * <li>{@code patients}: thêm thông tin sức khỏe do bệnh nhân tự khai (chiều cao, cân nặng, số thẻ BHYT, bệnh
 * nền, tiền sử gia đình). Bệnh nền do bác sĩ ghi vẫn nằm ở {@code medical_records}.</li>
 * </ul>
 *
 * <p><b>Chỉ cộng thêm và chạy lại được:</b> bảng và từng cột chỉ được tạo khi chưa có. Bản SQL tương đương để chạy
 * tay trên CSDL không dùng Flyway nằm ở {@code scripts/reviews-profiles-schema.mysql.sql}.
 */
@Component
public class V5__ReviewsAndProfiles extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V5__ReviewsAndProfiles.class);

    static final String TABLE = "doctor_reviews";

    static final Map<String, String> DOCTOR_COLUMNS = new LinkedHashMap<>();
    static final Map<String, String> PATIENT_COLUMNS = new LinkedHashMap<>();

    static {
        DOCTOR_COLUMNS.put("academic_title", "VARCHAR(100) DEFAULT NULL");
        DOCTOR_COLUMNS.put("years_of_experience", "SMALLINT DEFAULT NULL");
        DOCTOR_COLUMNS.put("education", "TEXT DEFAULT NULL");
        DOCTOR_COLUMNS.put("expertise", "TEXT DEFAULT NULL");
        DOCTOR_COLUMNS.put("work_history", "TEXT DEFAULT NULL");

        PATIENT_COLUMNS.put("height_cm", "SMALLINT DEFAULT NULL");
        PATIENT_COLUMNS.put("weight_kg", "DECIMAL(5,1) DEFAULT NULL");
        PATIENT_COLUMNS.put("health_insurance_number", "VARCHAR(20) DEFAULT NULL");
        PATIENT_COLUMNS.put("chronic_conditions", "TEXT DEFAULT NULL");
        PATIENT_COLUMNS.put("family_history", "TEXT DEFAULT NULL");
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            addMissingColumns(connection, statement, "doctors", DOCTOR_COLUMNS);
            addMissingColumns(connection, statement, "patients", PATIENT_COLUMNS);
            if (tableExists(connection, TABLE)) {
                log.info("Flyway V5: bảng {} đã có, bỏ qua.", TABLE);
                return;
            }
            statement.execute("""
                    CREATE TABLE doctor_reviews (
                      id BIGINT NOT NULL AUTO_INCREMENT,
                      appointment_id BIGINT NOT NULL,
                      patient_id BIGINT NOT NULL,
                      doctor_id BIGINT NOT NULL,
                      rating TINYINT NOT NULL,
                      comment VARCHAR(1000) DEFAULT NULL,
                      status VARCHAR(20) NOT NULL,
                      hidden_reason VARCHAR(500) DEFAULT NULL,
                      hidden_by BIGINT DEFAULT NULL,
                      hidden_at DATETIME(6) DEFAULT NULL,
                      version BIGINT NOT NULL DEFAULT 0,
                      created_at DATETIME(6) NOT NULL,
                      updated_at DATETIME(6) NOT NULL,
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_doctor_reviews_appointment (appointment_id),
                      KEY idx_doctor_reviews_doctor_status (doctor_id, status, created_at),
                      KEY idx_doctor_reviews_patient (patient_id),
                      CONSTRAINT chk_doctor_reviews_rating CHECK (rating BETWEEN 1 AND 5),
                      CONSTRAINT chk_doctor_reviews_status CHECK (status IN ('VISIBLE', 'HIDDEN')),
                      CONSTRAINT fk_doctor_reviews_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
                      CONSTRAINT fk_doctor_reviews_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
                      CONSTRAINT fk_doctor_reviews_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""");
        }
        log.info("Flyway V5: đã tạo bảng {}.", TABLE);
    }

    private static void addMissingColumns(Connection connection, Statement statement, String table,
            Map<String, String> columns) throws SQLException {
        for (Map.Entry<String, String> column : columns.entrySet()) {
            if (columnExists(connection, table, column.getKey())) {
                continue;
            }
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column.getKey() + " " + column.getValue());
            log.info("Flyway V5: đã thêm cột {}.{}.", table, column.getKey());
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
