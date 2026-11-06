-- =====================================================================
-- MedBook - ảnh đại diện bác sĩ và ảnh chuyên khoa, bản CHẠY TAY cho MySQL 8.0.16 trở lên
--
-- Tương đương migration Flyway V6 (config/migration/V6__MediaImages.java). Dùng cho CSDL đang chạy với Flyway TẮT
-- (hồ sơ online, v4mysql: ddl-auto=validate). Hồ sơ docker và prod tự chạy V6 qua Flyway nên KHÔNG cần tệp này.
--
-- * Chỉ cộng thêm: một bảng mới (media_images) và hai cột cho phép NULL (doctors.avatar_image_id,
--   specialties.image_id).
-- * Chạy lại được: mỗi mục chỉ được tạo khi chưa có.
-- * Chạy SAU reviews-profiles-schema.mysql.sql (V5) và TRƯỚC khi triển khai bản ứng dụng có chức năng tải ảnh.
--   Bản ứng dụng cũ vẫn chạy bình thường sau khi tệp này đã chạy.
--
-- Cách chạy (giữ --default-character-set để chú thích tiếng Việt không bị lỗi font):
--   mysql --default-character-set=utf8mb4 -h <máy chủ> -u <tài khoản> -p <tên CSDL> < scripts/media-images-schema.mysql.sql
-- Nên sao lưu trước (scripts/backup-db.ps1).
-- =====================================================================
SET NAMES utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'doctors' AND column_name = 'avatar_image_id') = 0,
              'ALTER TABLE doctors ADD COLUMN avatar_image_id BIGINT NULL',
              'SELECT ''doctors.avatar_image_id đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'specialties' AND column_name = 'image_id') = 0,
              'ALTER TABLE specialties ADD COLUMN image_id BIGINT NULL',
              'SELECT ''specialties.image_id đã có, bỏ qua'' AS ghi_chu');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS media_images (
  id BIGINT NOT NULL AUTO_INCREMENT,
  content_type VARCHAR(30) NOT NULL,
  size_bytes INT NOT NULL,
  data MEDIUMBLOB NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_media_images_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
  CONSTRAINT chk_media_images_size CHECK (size_bytes > 0 AND size_bytes <= 1048576)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT COUNT(*) AS so_anh FROM media_images;
