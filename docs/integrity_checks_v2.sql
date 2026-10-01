-- integrity_checks_v2.sql
-- Các kiểm tra chính cho V2 Buổi 05.

-- 1. Không có 2 appointment đang active cùng giữ một slot.
SELECT active_slot_id, COUNT(*) AS active_count
FROM appointments
WHERE active_slot_id IS NOT NULL
GROUP BY active_slot_id
HAVING COUNT(*) > 1;

-- 2. Appointment CANCELLED phải nhả active_slot_id.
SELECT id, slot_id, active_slot_id, status
FROM appointments
WHERE status = 'CANCELLED'
  AND active_slot_id IS NOT NULL;

-- 3. Appointment không CANCELLED phải giữ active_slot_id = slot_id.
SELECT id, slot_id, active_slot_id, status
FROM appointments
WHERE status <> 'CANCELLED'
  AND (active_slot_id IS NULL OR active_slot_id <> slot_id);

-- 4. Slot AVAILABLE phải is_available = 1.
SELECT id, status, is_available
FROM appointment_slots
WHERE status = 'AVAILABLE'
  AND is_available <> b'1';

-- 5. Slot BOOKED phải is_available = 0.
SELECT id, status, is_available
FROM appointment_slots
WHERE status = 'BOOKED'
  AND is_available <> b'0';

-- 6. Slot có thời gian không hợp lệ.
SELECT id, slot_date, start_time, end_time
FROM appointment_slots
WHERE end_time <= start_time;

-- 7. Appointment trỏ tới slot không tồn tại.
SELECT a.id, a.slot_id
FROM appointments a
LEFT JOIN appointment_slots s ON s.id = a.slot_id
WHERE s.id IS NULL;

-- 8. Thống kê chỉ mục phục vụ booking.
SHOW INDEX FROM appointment_slots;
SHOW INDEX FROM appointments;

-- 9. Kiểm tra truy vấn hot query.
EXPLAIN
SELECT id, doctor_id, slot_date, start_time, end_time, status, is_available
FROM appointment_slots
WHERE doctor_id = 1
  AND slot_date = '2026-10-05'
  AND status = 'AVAILABLE'
  AND is_available = b'1';
