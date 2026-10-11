-- =====================================================================
-- MedBook - bảng giữ chỗ có thời hạn (slot_holds), bản CHẠY TAY cho MySQL 8
--
-- Tương đương migration Flyway V4 (config/migration/V4__SlotHolds.java). Dùng cho CSDL đang chạy với Flyway TẮT
-- (hồ sơ online, v4mysql: ddl-auto=validate), ví dụ CSDL nghiệm thu. Hồ sơ docker và prod tự chạy V4 qua Flyway
-- nên KHÔNG cần tệp này.
--
-- * Chỉ cộng thêm: đúng một bảng mới. Không sửa, không xóa gì sẵn có.
-- * Chạy lại được: bảng đã có thì không làm gì.
-- * Chạy TRƯỚC khi triển khai bản ứng dụng có chức năng giữ chỗ (bản mới validate bảng này). Bản ứng dụng cũ vẫn
--   chạy bình thường sau khi tệp này đã chạy, vì nó không dùng tới bảng mới.
--
-- Cách chạy:
--   mysql -h <máy chủ> -u <tài khoản> -p <tên CSDL> < scripts/slot-holds-schema.mysql.sql
-- Nên sao lưu trước (scripts/backup-db.ps1).
-- =====================================================================

CREATE TABLE IF NOT EXISTS slot_holds (
  id BIGINT NOT NULL AUTO_INCREMENT,
  slot_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_slot_holds_slot (slot_id),
  KEY idx_slot_holds_expires (expires_at),
  KEY idx_slot_holds_user (user_id),
  CONSTRAINT fk_slot_holds_slot FOREIGN KEY (slot_id) REFERENCES appointment_slots (id) ON DELETE CASCADE,
  CONSTRAINT fk_slot_holds_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT COUNT(*) AS so_ban_giu_cho FROM slot_holds;
