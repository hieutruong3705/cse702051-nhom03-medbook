-- ============================================================
-- BUOI 5 - V2 INTEGRITY CHECKS
-- ============================================================

-- CHECK 1: Tong so slot va trang thai
SELECT
    COUNT(*) AS total_slots,
    SUM(status = 'AVAILABLE' AND is_available = b'1') AS available_slots,
    SUM(status = 'BOOKED' AND is_available = b'0') AS booked_slots,
    SUM(status = 'CANCELLED' AND is_available = b'1') AS cancelled_slots
FROM appointment_slots;

-- CHECK 2: Slot khong co doctor
SELECT aps.id, aps.doctor_id
FROM appointment_slots aps
LEFT JOIN doctors d ON d.id = aps.doctor_id
WHERE d.id IS NULL;

-- CHECK 3: Appointment khong co slot
SELECT id, patient_id, doctor_id, slot_id, active_slot_id, status
FROM appointments
WHERE slot_id IS NULL;

-- CHECK 4: Khong co hai appointment dang giu cung active slot
SELECT active_slot_id, COUNT(*) AS active_appointment_count
FROM appointments
WHERE active_slot_id IS NOT NULL
GROUP BY active_slot_id
HAVING COUNT(*) > 1;

-- CHECK 5: Appointment dang hoat dong phai giu dung slot
SELECT id, slot_id, active_slot_id, status
FROM appointments
WHERE status <> 'CANCELLED'
  AND (active_slot_id IS NULL OR active_slot_id <> slot_id);

-- CHECK 6: Appointment CANCELLED phai release active slot
SELECT id, slot_id, active_slot_id, status
FROM appointments
WHERE status = 'CANCELLED'
  AND active_slot_id IS NOT NULL;

-- CHECK 7: Trang thai slot va is_available
SELECT id, is_available, status
FROM appointment_slots
WHERE (status = 'AVAILABLE' AND is_available <> b'1')
   OR (status = 'BOOKED' AND is_available <> b'0')
   OR (status = 'CANCELLED' AND is_available NOT IN (b'0', b'1'));

-- CHECK 8: Thoi gian slot
SELECT id, slot_date, start_time, end_time
FROM appointment_slots
WHERE end_time <= start_time;

-- CHECK 9: FK appointments.slot_id
SELECT a.id AS appointment_id, a.slot_id
FROM appointments a
LEFT JOIN appointment_slots aps ON aps.id = a.slot_id
WHERE aps.id IS NULL;

-- CHECK 10: Index
SHOW INDEX FROM appointment_slots;
SHOW INDEX FROM appointments;

-- CHECK 11: EXPLAIN tim slot
EXPLAIN
SELECT id, doctor_id, slot_date, start_time, end_time,
       is_available, status
FROM appointment_slots
WHERE doctor_id = (
    SELECT id FROM doctors ORDER BY id LIMIT 1
)
  AND slot_date = '2026-10-05'
  AND status = 'AVAILABLE'
  AND is_available = b'1'
ORDER BY start_time;

-- CHECK 12: EXPLAIN appointment theo patient
EXPLAIN
SELECT id, patient_id, doctor_id, slot_id, active_slot_id,
       status, created_at
FROM appointments
WHERE patient_id = (
    SELECT id FROM patients ORDER BY id LIMIT 1
)
ORDER BY created_at DESC;

-- CHECK 13: Danh sach slot
SELECT id, doctor_id, slot_date, start_time, end_time,
       is_available, status, note, version
FROM appointment_slots
ORDER BY slot_date, start_time;

-- CHECK 14: Danh sach appointment
SELECT id, patient_id, doctor_id, slot_id, active_slot_id,
       status, notes, cancelled_at, created_at, updated_at
FROM appointments
ORDER BY id;
