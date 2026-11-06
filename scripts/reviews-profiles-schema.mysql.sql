-- =====================================================================
-- MedBook - đánh giá lần khám và hồ sơ mở rộng, bản CHẠY TAY cho MySQL 8.0.16 trở lên
--
-- Tương đương migration Flyway V5 (config/migration/V5__ReviewsAndProfiles.java). Dùng cho CSDL đang chạy với
-- Flyway TẮT (hồ sơ online, v4mysql: ddl-auto=validate), ví dụ CSDL nghiệm thu. Hồ sơ docker và prod tự chạy V5
-- qua Flyway nên KHÔNG cần tệp này.
--
-- * Chỉ cộng thêm: một bảng mới (doctor_reviews) và mười cột cho phép NULL ở doctors, patients.
-- * Chạy lại được: mỗi mục chỉ được tạo khi chưa có.
-- * Chạy TRƯỚC khi triển khai bản ứng dụng có chức năng đánh giá (bản mới validate các cột và bảng này). Bản ứng
--   dụng cũ vẫn chạy bình thường sau khi tệp này đã chạy, vì mọi thứ thêm vào đều không bắt buộc với nó.
--
-- Cách chạy:
--   mysql -h <máy chủ> -u <tài khoản> -p <tên CSDL> < scripts/reviews-profiles-schema.mysql.sql
-- Nên sao lưu trước (scripts/backup-db.ps1).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Cột mới (MySQL không có ADD COLUMN IF NOT EXISTS nên kiểm tra qua information_schema)
-- ---------------------------------------------------------------------
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'doctors' AND column_name = 'academic_title') = 0,
              'ALTER TABLE doctors ADD COLUMN academic_title VARCHAR(100) NULL',
              'SELECT ''doctors.academic_title đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'doctors' AND column_name = 'years_of_experience') = 0,
              'ALTER TABLE doctors ADD COLUMN years_of_experience SMALLINT NULL',
              'SELECT ''doctors.years_of_experience đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'doctors' AND column_name = 'education') = 0,
              'ALTER TABLE doctors ADD COLUMN education TEXT NULL',
              'SELECT ''doctors.education đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'doctors' AND column_name = 'expertise') = 0,
              'ALTER TABLE doctors ADD COLUMN expertise TEXT NULL',
              'SELECT ''doctors.expertise đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'doctors' AND column_name = 'work_history') = 0,
              'ALTER TABLE doctors ADD COLUMN work_history TEXT NULL',
              'SELECT ''doctors.work_history đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'patients' AND column_name = 'height_cm') = 0,
              'ALTER TABLE patients ADD COLUMN height_cm SMALLINT NULL',
              'SELECT ''patients.height_cm đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'patients' AND column_name = 'weight_kg') = 0,
              'ALTER TABLE patients ADD COLUMN weight_kg DECIMAL(5,1) NULL',
              'SELECT ''patients.weight_kg đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'patients' AND column_name = 'health_insurance_number') = 0,
              'ALTER TABLE patients ADD COLUMN health_insurance_number VARCHAR(20) NULL',
              'SELECT ''patients.health_insurance_number đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'patients' AND column_name = 'chronic_conditions') = 0,
              'ALTER TABLE patients ADD COLUMN chronic_conditions TEXT NULL',
              'SELECT ''patients.chronic_conditions đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'patients' AND column_name = 'family_history') = 0,
              'ALTER TABLE patients ADD COLUMN family_history TEXT NULL',
              'SELECT ''patients.family_history đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. Bảng mới
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doctor_reviews (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT COUNT(*) AS so_danh_gia FROM doctor_reviews;
