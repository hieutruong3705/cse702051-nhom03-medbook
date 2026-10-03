-- =====================================================================
-- Buổi 06 — V2 Phụ trách dữ liệu
-- Kịch bản lược đồ có ràng buộc bất biến ở mức cơ sở dữ liệu
--
-- Áp dụng cho MySQL 8.0.16 trở lên (từ bản này MySQL mới thực sự cưỡng
-- chế CHECK; bản thấp hơn chấp nhận cú pháp rồi bỏ qua).
-- Kịch bản chỉ BỔ SUNG, không sửa và không xóa cột sẵn có, nên chạy
-- được trên cơ sở dữ liệu đã triển khai đang ở chế độ validate.
-- Chạy sau khi lược đồ đã được tạo.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Bất biến thời gian: giờ kết thúc phải sau giờ bắt đầu
-- ---------------------------------------------------------------------
ALTER TABLE appointment_slots
  ADD CONSTRAINT chk_slots_time CHECK (end_time > start_time);

ALTER TABLE doctor_schedules
  ADD CONSTRAINT chk_schedules_time CHECK (end_time > start_time);

ALTER TABLE schedule_breaks
  ADD CONSTRAINT chk_breaks_time CHECK (end_time > start_time);

-- ---------------------------------------------------------------------
-- 2. Bất biến trạng thái: chỉ nhận giá trị trong tập cho phép
-- ---------------------------------------------------------------------
ALTER TABLE appointment_slots
  ADD CONSTRAINT chk_slots_status
  CHECK (status IN ('AVAILABLE', 'BOOKED', 'CANCELLED'));

ALTER TABLE invoices
  ADD CONSTRAINT chk_invoices_status
  CHECK (status IN ('UNPAID', 'PAID', 'VOID'));

-- ---------------------------------------------------------------------
-- 3. Bất biến tiền tệ và số lượng: không âm, chiết khấu không vượt tổng
-- ---------------------------------------------------------------------
ALTER TABLE services
  ADD CONSTRAINT chk_services_price CHECK (price >= 0);

ALTER TABLE services
  ADD CONSTRAINT chk_services_duration CHECK (duration_minutes > 0);

ALTER TABLE invoices
  ADD CONSTRAINT chk_invoices_amount
  CHECK (subtotal >= 0 AND discount_amount >= 0 AND total_amount >= 0
         AND discount_amount <= subtotal);

ALTER TABLE invoice_items
  ADD CONSTRAINT chk_invoice_items
  CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0);

ALTER TABLE prescription_items
  ADD CONSTRAINT chk_presc_items_qty CHECK (quantity > 0);

-- ---------------------------------------------------------------------
-- 4. Chỉ mục cho truy vấn nóng: tìm giờ trống theo bác sĩ và ngày
--    Phục vụ GET /api/v1/doctor-schedules/slots và
--            GET /api/v1/appointment-slots/available
-- ---------------------------------------------------------------------
CREATE INDEX idx_appointment_slots_booking
  ON appointment_slots (doctor_id, slot_date, status, is_available);

-- ---------------------------------------------------------------------
-- 5. Đối soát sau khi chạy
-- ---------------------------------------------------------------------
-- SELECT CONSTRAINT_NAME, CHECK_CLAUSE
--   FROM information_schema.CHECK_CONSTRAINTS
--  WHERE CONSTRAINT_SCHEMA = DATABASE();
--
-- SHOW INDEX FROM appointment_slots;
