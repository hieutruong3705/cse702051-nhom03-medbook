-- migration_v2_buoi5.sql
-- Chuyển schema appointments hiện có sang mô hình V4:
-- slot_id giữ lịch sử; active_slot_id UNIQUE chỉ giữ slot đang bị chiếm.
-- Không dùng schedules/schedule_id và không dùng PENDING.

ALTER TABLE appointments
    DROP INDEX uk_appointments_slot;

ALTER TABLE appointments
    ADD COLUMN active_slot_id BIGINT UNSIGNED NULL AFTER slot_id,
    ADD COLUMN service_id BIGINT UNSIGNED NULL AFTER active_slot_id,
    ADD COLUMN cancel_reason VARCHAR(500) NULL AFTER notes,
    ADD COLUMN cancelled_at DATETIME(6) NULL AFTER cancel_reason,
    ADD COLUMN reminder_24h_sent_at DATETIME(6) NULL AFTER cancelled_at,
    ADD COLUMN reminder_2h_sent_at DATETIME(6) NULL AFTER reminder_24h_sent_at;

UPDATE appointments
SET active_slot_id =
    CASE
        WHEN status = 'CANCELLED' THEN NULL
        ELSE slot_id
    END;

ALTER TABLE appointments
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'BOOKED',
    ADD UNIQUE KEY uq_appointments_active_slot (active_slot_id),
    ADD KEY idx_appointments_slot (slot_id);

-- FK slot được giữ/chuẩn hóa; nếu FK cũ tồn tại với tên khác,
-- kiểm tra SHOW CREATE TABLE appointments trước khi chạy phần này.
ALTER TABLE appointments
    ADD CONSTRAINT fk_appointments_slot_v2
    FOREIGN KEY (slot_id) REFERENCES appointment_slots(id)
    ON UPDATE CASCADE;
