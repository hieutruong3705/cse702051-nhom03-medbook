package com.phenikaa.cse702051.medbook.config.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * V2: các thay đổi lược đồ của đợt hoàn thiện (thông báo, ngày nghỉ của bác sĩ, thuốc theo danh mục, hủy hóa đơn,
 * độ dài slot của ca) và chỉ mục cho các truy vấn danh sách, báo cáo.
 *
 * <p><b>Chỉ cộng thêm và chạy lại được:</b> mỗi bảng, cột, chỉ mục chỉ được tạo khi chưa có. Nhờ vậy cùng một
 * migration chạy đúng trên CSDL mới (sau V1), trên CSDL nghiệm thu đang ở trạng thái V1, và trên CSDL từng được
 * Hibernate tự cập nhật ({@code ddl-auto=update}) nên đã có sẵn một phần các thay đổi này. Không sửa, không xóa
 * cột hay dữ liệu nào.
 *
 * <p>Viết bằng Java thay vì SQL vì MySQL không có {@code ADD COLUMN IF NOT EXISTS}; cách kiểm tra qua
 * {@link DatabaseMetaData} chạy được trên cả MySQL lẫn H2 (dùng trong test). Bản SQL tương đương để chạy tay nằm ở
 * {@code scripts/cx-additive-schema.mysql.sql}.
 */
@Component
public class V2__CxAdditiveSchema extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V2__CxAdditiveSchema.class);

    private static final String TABLE_OPTIONS = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    @Override
    public void migrate(Context context) throws Exception {
        Schema schema = new Schema(context.getConnection());

        // ---------- Bảng mới ----------
        schema.createTable("notifications", """
                CREATE TABLE notifications (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  type VARCHAR(40) NOT NULL,
                  title VARCHAR(150) NOT NULL,
                  message VARCHAR(500) NOT NULL,
                  appointment_id BIGINT DEFAULT NULL,
                  created_at DATETIME(6) NOT NULL,
                  read_at DATETIME(6) DEFAULT NULL,
                  PRIMARY KEY (id),
                  KEY idx_notifications_user_created (user_id, created_at),
                  KEY idx_notifications_user_read (user_id, read_at),
                  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
                )""" + TABLE_OPTIONS);
        schema.createTable("doctor_day_offs", """
                CREATE TABLE doctor_day_offs (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  doctor_id BIGINT NOT NULL,
                  off_date DATE NOT NULL,
                  reason VARCHAR(255) DEFAULT NULL,
                  created_at DATETIME(6) NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_doctor_day_offs_doctor_date (doctor_id, off_date),
                  CONSTRAINT fk_doctor_day_offs_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id)
                )""" + TABLE_OPTIONS);

        // ---------- Cột mới (đều cho phép NULL nên dữ liệu cũ không phải sửa) ----------
        schema.addColumn("prescription_items", "medicine_id", "BIGINT DEFAULT NULL");
        schema.addColumn("invoices", "voided_at", "DATETIME(6) DEFAULT NULL");
        schema.addColumn("invoices", "void_reason", "VARCHAR(255) DEFAULT NULL");
        schema.addColumn("doctor_schedules", "slot_minutes", "INT DEFAULT NULL");

        // ---------- Chỉ mục cho danh sách, tra cứu và báo cáo ----------
        schema.createIndex("appointments", "idx_appointments_patient", "patient_id");
        schema.createIndex("appointments", "idx_appointments_doctor_status", "doctor_id", "status");
        schema.createIndex("doctor_schedules", "idx_doctor_schedules_doctor_date", "doctor_id", "work_date");
        schema.createIndex("encounters", "idx_encounters_doctor_status", "doctor_id", "status");
        schema.createIndex("encounters", "idx_encounters_record_time", "medical_record_id", "encounter_at");
        schema.createIndex("attachments", "idx_attachments_encounter", "encounter_id");
        schema.createIndex("prescriptions", "idx_prescriptions_encounter", "encounter_id");
        schema.createIndex("prescription_items", "idx_prescription_items_prescription", "prescription_id");
        schema.createIndex("prescription_items", "idx_prescription_items_medicine", "medicine_id");
        schema.createIndex("invoices", "idx_invoices_patient_status", "patient_id", "status");
        schema.createIndex("invoices", "idx_invoices_issued", "issued_at");
        schema.createIndex("invoice_items", "idx_invoice_items_invoice", "invoice_id");
        schema.createIndex("invoice_items", "idx_invoice_items_service", "service_id");
        schema.createIndex("audit_logs", "idx_audit_logs_created", "created_at");
        schema.createIndex("audit_logs", "idx_audit_logs_action_created", "action_code", "created_at");
        schema.createIndex("audit_logs", "idx_audit_logs_entity", "entity_type", "entity_id");

        // ---------- Ràng buộc bất biến, chỉ thêm khi dữ liệu hiện có không vi phạm ----------
        if (schema.isEmpty("SELECT 1 FROM doctors WHERE license_number IS NOT NULL "
                + "GROUP BY license_number HAVING COUNT(*) > 1")) {
            schema.createUniqueIndex("doctors", "uk_doctors_license", "license_number");
        } else {
            log.warn("Bỏ qua UNIQUE(doctors.license_number): đang có số giấy phép trùng nhau, cần xử lý dữ liệu trước.");
        }
        if (schema.isEmpty("SELECT 1 FROM doctor_schedules WHERE slot_minutes IS NOT NULL "
                + "AND (slot_minutes < 5 OR slot_minutes > 120)")) {
            schema.addCheck("doctor_schedules", "chk_schedules_slot_minutes",
                    "slot_minutes IS NULL OR (slot_minutes >= 5 AND slot_minutes <= 120)");
        } else {
            log.warn("Bỏ qua CHECK(doctor_schedules.slot_minutes): đang có ca với độ dài slot ngoài khoảng 5-120.");
        }
    }

    /** Các thao tác "chỉ tạo khi chưa có" trên một kết nối. */
    private static final class Schema {

        private final Connection connection;
        private final DatabaseMetaData metaData;
        private final String catalog;
        private final String schemaName;

        Schema(Connection connection) throws SQLException {
            this.connection = connection;
            this.metaData = connection.getMetaData();
            this.catalog = connection.getCatalog();
            this.schemaName = connection.getSchema(); // MySQL: null (dùng catalog); H2: PUBLIC
        }

        void createTable(String table, String ddl) throws SQLException {
            if (resolveTable(table) != null) {
                log.info("Bảng {} đã có, bỏ qua.", table);
                return;
            }
            execute(ddl);
        }

        void addColumn(String table, String column, String definition) throws SQLException {
            String actualTable = requireTable(table);
            try (ResultSet columns = metaData.getColumns(catalog, schemaName, actualTable, null)) {
                while (columns.next()) {
                    if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        log.info("Cột {}.{} đã có, bỏ qua.", table, column);
                        return;
                    }
                }
            }
            execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }

        void createIndex(String table, String name, String... columns) throws SQLException {
            createIndex(table, name, false, columns);
        }

        void createUniqueIndex(String table, String name, String... columns) throws SQLException {
            createIndex(table, name, true, columns);
        }

        /**
         * Bỏ qua khi đã có chỉ mục cùng tên, hoặc đã có chỉ mục khác bắt đầu bằng đúng các cột này (ví dụ chỉ mục
         * Hibernate tự tạo cho khóa ngoại hay UNIQUE): thêm nữa chỉ tốn chỗ và làm chậm ghi.
         */
        private void createIndex(String table, String name, boolean unique, String... columns) throws SQLException {
            String actualTable = requireTable(table);
            Map<String, Map<Integer, String>> indexes = new LinkedHashMap<>();
            Map<String, Boolean> uniqueness = new LinkedHashMap<>();
            try (ResultSet rows = metaData.getIndexInfo(catalog, schemaName, actualTable, false, false)) {
                while (rows.next()) {
                    String indexName = rows.getString("INDEX_NAME");
                    String column = rows.getString("COLUMN_NAME");
                    if (indexName == null || column == null) {
                        continue;
                    }
                    indexes.computeIfAbsent(indexName, key -> new TreeMap<>())
                            .put((int) rows.getShort("ORDINAL_POSITION"), column.toLowerCase(Locale.ROOT));
                    uniqueness.put(indexName, !rows.getBoolean("NON_UNIQUE"));
                }
            }
            List<String> wanted = Arrays.stream(columns).map(c -> c.toLowerCase(Locale.ROOT)).toList();
            for (Map.Entry<String, Map<Integer, String>> index : indexes.entrySet()) {
                List<String> existing = new ArrayList<>(index.getValue().values());
                boolean sameName = index.getKey().equalsIgnoreCase(name);
                boolean covers = existing.size() >= wanted.size() && existing.subList(0, wanted.size()).equals(wanted);
                boolean strongEnough = !unique || (uniqueness.get(index.getKey()) && existing.size() == wanted.size());
                if (sameName || (covers && strongEnough)) {
                    log.info("Chỉ mục {} trên {}{} đã được {} đáp ứng, bỏ qua.", name, table, wanted, index.getKey());
                    return;
                }
            }
            execute("CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + name + " ON " + table
                    + " (" + String.join(", ", columns) + ")");
        }

        void addCheck(String table, String name, String condition) throws SQLException {
            requireTable(table);
            String owner = schemaName != null ? schemaName : catalog;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE LOWER(constraint_schema) = LOWER(?) AND LOWER(constraint_name) = LOWER(?)")) {
                statement.setString(1, owner);
                statement.setString(2, name);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next() && result.getLong(1) > 0) {
                        log.info("Ràng buộc {} đã có, bỏ qua.", name);
                        return;
                    }
                }
            }
            execute("ALTER TABLE " + table + " ADD CONSTRAINT " + name + " CHECK (" + condition + ")");
        }

        boolean isEmpty(String query) throws SQLException {
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(query)) {
                return !result.next();
            }
        }

        private String requireTable(String table) throws SQLException {
            String actual = resolveTable(table);
            if (actual == null) {
                throw new SQLException("Thiếu bảng " + table + ": lược đồ nền (V1) chưa được áp dụng.");
            }
            return actual;
        }

        /** Tên bảng đúng kiểu chữ mà CSDL đang lưu (MySQL trên Linux phân biệt hoa thường); null nếu chưa có. */
        private String resolveTable(String table) throws SQLException {
            try (ResultSet tables = metaData.getTables(catalog, schemaName, null, new String[] { "TABLE" })) {
                while (tables.next()) {
                    String name = tables.getString("TABLE_NAME");
                    if (table.equalsIgnoreCase(name)) {
                        return name;
                    }
                }
            }
            return null;
        }

        private void execute(String sql) throws SQLException {
            log.info("Flyway V2: {}", sql.lines().findFirst().orElse(sql).trim());
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }
}
