-- =====================================================================
-- MedBook - thêm vai trò Lễ tân (RECEPTIONIST), bản CHẠY TAY cho MySQL 8
--
-- Tương đương migration Flyway V3 (config/migration/V3__ReceptionistRole.java). Dùng cho CSDL đang chạy với
-- Flyway TẮT (hồ sơ online, v4mysql), ví dụ CSDL nghiệm thu. Hồ sơ docker và prod tự chạy V3 qua Flyway nên
-- KHÔNG cần tệp này.
--
-- * Chỉ thêm DỮ LIỆU: một dòng trong bảng roles. Không đổi lược đồ.
-- * Chạy lại được: đã có vai trò thì không chèn thêm.
-- * Chạy TRƯỚC khi triển khai bản ứng dụng có khu Lễ tân. Sau đó Quản trị viên tạo tài khoản lễ tân ở trang
--   Tài khoản; tệp này không tạo tài khoản nào.
--
-- Cách chạy:
--   mysql -h <máy chủ> -u <tài khoản> -p <tên CSDL> < scripts/receptionist-role.mysql.sql
-- Nên sao lưu trước (scripts/backup-db.ps1).
-- =====================================================================

INSERT INTO roles (code, name, description, created_at)
SELECT 'RECEPTIONIST', 'Lễ tân', 'Nhân viên tiếp đón tại quầy', NOW(6)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code = 'RECEPTIONIST');

SELECT id, code, name FROM roles ORDER BY id;
