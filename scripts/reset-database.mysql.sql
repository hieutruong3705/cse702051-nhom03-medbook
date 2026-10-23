-- =====================================================================
-- MedBook - XÓA TOÀN BỘ bảng của MedBook cùng mọi dữ liệu trong đó, bản CHẠY TAY cho MySQL 8
--
-- Dùng khi muốn dựng lại một cơ sở dữ liệu từ đầu với dữ liệu sạch (bỏ hết tài khoản, lịch hẹn, bệnh án, hóa đơn
-- và dữ liệu mẫu cũ). KHÔNG hoàn tác được: hãy sao lưu trước nếu còn cần dữ liệu (scripts/backup-db.ps1).
--
-- * Chỉ xóa các bảng của MedBook liệt kê bên dưới (kể cả bảng lịch sử Flyway); bảng khác trong cùng CSDL được giữ.
-- * Tệp không tự chọn CSDL: phải chỉ rõ tên CSDL khi chạy, kiểm tra kỹ trước khi nhấn Enter.
-- * Chạy lại được: bảng đã xóa rồi thì bỏ qua.
-- * Tệp đính kèm đã tải lên nằm ngoài CSDL (thư mục MEDBOOK_UPLOAD_DIR, volume "uploads" của Docker Compose) và
--   không bị tệp này xóa.
--
-- Cách chạy:
--   mysql -h <máy chủ> -u <tài khoản> -p <tên CSDL> < scripts/reset-database.mysql.sql
--
-- Sau khi chạy:
--   * Flyway bật (hồ sơ docker, prod): khởi động lại ứng dụng, Flyway tự tạo lại lược đồ.
--   * Flyway tắt (hồ sơ online, v4mysql): nạp database/schema.sql rồi mới khởi động ứng dụng.
--   * Tài khoản quản trị ban đầu: đặt MEDBOOK_ADMIN_INITIAL_PASSWORD trước khi khởi động (README.md, mục 5).
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS
  slot_holds,
  notifications,
  attachments,
  invoice_items,
  invoices,
  prescription_items,
  prescriptions,
  medical_records,
  encounters,
  appointments,
  appointment_slots,
  schedule_breaks,
  doctor_schedules,
  doctor_day_offs,
  doctors,
  patients,
  medicines,
  services,
  specialties,
  audit_logs,
  auth_sessions,
  refresh_tokens,
  revoked_tokens,
  password_reset_tokens,
  user_roles,
  roles,
  users,
  flyway_schema_history;

SET FOREIGN_KEY_CHECKS = 1;

-- Kết quả mong đợi: không còn dòng nào (hoặc chỉ còn bảng không thuộc MedBook).
SELECT table_name AS bang_con_lai FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name;
