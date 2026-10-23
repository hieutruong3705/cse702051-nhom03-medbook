-- =====================================================================
-- MedBook - thay đổi lược đồ của đợt hoàn thiện, bản CHẠY TAY cho MySQL 8.0.16 trở lên
--
-- Tương đương migration Flyway V2 (config/migration/V2__CxAdditiveSchema.java). Dùng cho CSDL đang chạy với
-- Flyway TẮT (hồ sơ online, v4mysql: ddl-auto=validate), ví dụ CSDL nghiệm thu. Hồ sơ docker và prod tự chạy V2
-- qua Flyway nên KHÔNG cần tệp này.
--
-- * Chỉ cộng thêm: bảng mới, cột mới cho phép NULL, chỉ mục, hai ràng buộc. Không sửa, không xóa gì sẵn có.
-- * Chạy lại được: mỗi mục chỉ được tạo khi chưa có, nên chạy nhiều lần không lỗi và không đổi gì thêm.
-- * Chạy TRƯỚC khi triển khai bản ứng dụng mới (bản mới validate các cột và bảng này). Bản ứng dụng cũ vẫn chạy
--   bình thường sau khi tệp này đã chạy, vì mọi thứ thêm vào đều không bắt buộc với nó.
--
-- Cách chạy:
--   mysql -h <máy chủ> -u <tài khoản> -p <tên CSDL> < scripts/cx-additive-schema.mysql.sql
-- Nên sao lưu trước (scripts/backup-db.ps1).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Bảng mới
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS doctor_day_offs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  doctor_id BIGINT NOT NULL,
  off_date DATE NOT NULL,
  reason VARCHAR(255) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_doctor_day_offs_doctor_date (doctor_id, off_date),
  CONSTRAINT fk_doctor_day_offs_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. Cột mới (MySQL không có ADD COLUMN IF NOT EXISTS nên kiểm tra qua information_schema)
-- ---------------------------------------------------------------------
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'prescription_items' AND column_name = 'medicine_id') = 0,
              'ALTER TABLE prescription_items ADD COLUMN medicine_id BIGINT NULL',
              'SELECT ''prescription_items.medicine_id đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'invoices' AND column_name = 'voided_at') = 0,
              'ALTER TABLE invoices ADD COLUMN voided_at DATETIME(6) NULL',
              'SELECT ''invoices.voided_at đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'invoices' AND column_name = 'void_reason') = 0,
              'ALTER TABLE invoices ADD COLUMN void_reason VARCHAR(255) NULL',
              'SELECT ''invoices.void_reason đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'doctor_schedules' AND column_name = 'slot_minutes') = 0,
              'ALTER TABLE doctor_schedules ADD COLUMN slot_minutes INT NULL',
              'SELECT ''doctor_schedules.slot_minutes đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. Chỉ mục cho danh sách, tra cứu và báo cáo
-- ---------------------------------------------------------------------
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'appointments' AND index_name = 'idx_appointments_patient') = 0,
              'CREATE INDEX idx_appointments_patient ON appointments (patient_id)',
              'SELECT ''idx_appointments_patient đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'appointments' AND index_name = 'idx_appointments_doctor_status') = 0,
              'CREATE INDEX idx_appointments_doctor_status ON appointments (doctor_id, status)',
              'SELECT ''idx_appointments_doctor_status đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'doctor_schedules' AND index_name = 'idx_doctor_schedules_doctor_date') = 0,
              'CREATE INDEX idx_doctor_schedules_doctor_date ON doctor_schedules (doctor_id, work_date)',
              'SELECT ''idx_doctor_schedules_doctor_date đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'encounters' AND index_name = 'idx_encounters_doctor_status') = 0,
              'CREATE INDEX idx_encounters_doctor_status ON encounters (doctor_id, status)',
              'SELECT ''idx_encounters_doctor_status đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'encounters' AND index_name = 'idx_encounters_record_time') = 0,
              'CREATE INDEX idx_encounters_record_time ON encounters (medical_record_id, encounter_at)',
              'SELECT ''idx_encounters_record_time đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'attachments' AND index_name = 'idx_attachments_encounter') = 0,
              'CREATE INDEX idx_attachments_encounter ON attachments (encounter_id)',
              'SELECT ''idx_attachments_encounter đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'prescriptions' AND index_name = 'idx_prescriptions_encounter') = 0,
              'CREATE INDEX idx_prescriptions_encounter ON prescriptions (encounter_id)',
              'SELECT ''idx_prescriptions_encounter đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'prescription_items' AND index_name = 'idx_prescription_items_prescription') = 0,
              'CREATE INDEX idx_prescription_items_prescription ON prescription_items (prescription_id)',
              'SELECT ''idx_prescription_items_prescription đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'prescription_items' AND index_name = 'idx_prescription_items_medicine') = 0,
              'CREATE INDEX idx_prescription_items_medicine ON prescription_items (medicine_id)',
              'SELECT ''idx_prescription_items_medicine đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'invoices' AND index_name = 'idx_invoices_patient_status') = 0,
              'CREATE INDEX idx_invoices_patient_status ON invoices (patient_id, status)',
              'SELECT ''idx_invoices_patient_status đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'invoices' AND index_name = 'idx_invoices_issued') = 0,
              'CREATE INDEX idx_invoices_issued ON invoices (issued_at)',
              'SELECT ''idx_invoices_issued đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'invoice_items' AND index_name = 'idx_invoice_items_invoice') = 0,
              'CREATE INDEX idx_invoice_items_invoice ON invoice_items (invoice_id)',
              'SELECT ''idx_invoice_items_invoice đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'invoice_items' AND index_name = 'idx_invoice_items_service') = 0,
              'CREATE INDEX idx_invoice_items_service ON invoice_items (service_id)',
              'SELECT ''idx_invoice_items_service đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'audit_logs' AND index_name = 'idx_audit_logs_created') = 0,
              'CREATE INDEX idx_audit_logs_created ON audit_logs (created_at)',
              'SELECT ''idx_audit_logs_created đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'audit_logs' AND index_name = 'idx_audit_logs_action_created') = 0,
              'CREATE INDEX idx_audit_logs_action_created ON audit_logs (action_code, created_at)',
              'SELECT ''idx_audit_logs_action_created đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'audit_logs' AND index_name = 'idx_audit_logs_entity') = 0,
              'CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id)',
              'SELECT ''idx_audit_logs_entity đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. Ràng buộc bất biến, chỉ thêm khi dữ liệu hiện có không vi phạm
-- ---------------------------------------------------------------------
-- Mỗi số giấy phép hành nghề chỉ thuộc về một bác sĩ.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'doctors' AND column_name = 'license_number'
                   AND non_unique = 0) = 0
              AND (SELECT COUNT(*) FROM (SELECT license_number FROM doctors WHERE license_number IS NOT NULL
                                         GROUP BY license_number HAVING COUNT(*) > 1) trung) = 0,
              'CREATE UNIQUE INDEX uk_doctors_license ON doctors (license_number)',
              'SELECT ''Bỏ qua uk_doctors_license: đã có, hoặc đang có số giấy phép trùng cần xử lý trước'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Độ dài slot của ca: để trống (ca tạo trước đây) hoặc từ 5 đến 120 phút.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints
                 WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_schedules_slot_minutes') = 0
              AND (SELECT COUNT(*) FROM doctor_schedules
                   WHERE slot_minutes IS NOT NULL AND (slot_minutes < 5 OR slot_minutes > 120)) = 0,
              'ALTER TABLE doctor_schedules ADD CONSTRAINT chk_schedules_slot_minutes CHECK (slot_minutes IS NULL OR (slot_minutes >= 5 AND slot_minutes <= 120))',
              'SELECT ''Bỏ qua chk_schedules_slot_minutes: đã có, hoặc đang có ca vi phạm'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 5. Đối soát sau khi chạy (mỗi dòng phải trả đúng số ghi bên cạnh)
-- ---------------------------------------------------------------------
SELECT 'bảng mới' AS muc, COUNT(*) AS thuc_te, 2 AS mong_doi FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name IN ('notifications', 'doctor_day_offs')
UNION ALL
SELECT 'cột mới', COUNT(*), 4 FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND ((table_name = 'prescription_items' AND column_name = 'medicine_id')
     OR (table_name = 'invoices' AND column_name IN ('voided_at', 'void_reason'))
     OR (table_name = 'doctor_schedules' AND column_name = 'slot_minutes'));
