USE medbook_db;

-- ============================================================
-- BUOI 5 - V2 INTEGRITY CHECKS
-- ============================================================

-- ------------------------------------------------------------
-- CHECK 1: Dem appointment slots
-- ------------------------------------------------------------
SELECT
    COUNT(*) AS total_slots,
    SUM(status = 'AVAILABLE' AND is_available = b'1')
        AS available_slots,
    SUM(status = 'BOOKED' AND is_available = b'0')
        AS booked_slots,
    SUM(status = 'CANCELLED')
        AS cancelled_slots
FROM appointment_slots;


-- ------------------------------------------------------------
-- CHECK 2: Slot phai co doctor ton tai
-- ------------------------------------------------------------
SELECT s.*
FROM appointment_slots s
LEFT JOIN doctors d ON d.id = s.doctor_id
WHERE d.id IS NULL;


-- ------------------------------------------------------------
-- CHECK 3: Appointment phai tham chieu slot ton tai
-- ------------------------------------------------------------
SELECT a.*
FROM appointments a
LEFT JOIN appointment_slots s ON s.id = a.slot_id
WHERE s.id IS NULL;


-- ------------------------------------------------------------
-- CHECK 4: Khong duoc co nhieu appointment cung slot
-- ------------------------------------------------------------
SELECT
    slot_id,
    COUNT(*) AS appointment_count
FROM appointments
GROUP BY slot_id
HAVING COUNT(*) > 1;


-- ------------------------------------------------------------
-- CHECK 5: Kiem tra trang thai slot
-- ------------------------------------------------------------
SELECT
    status,
    is_available,
    COUNT(*) AS total
FROM appointment_slots
GROUP BY status, is_available
ORDER BY status, is_available;


-- ------------------------------------------------------------
-- CHECK 6: Kiem tra slot co thoi gian sai
-- ------------------------------------------------------------
SELECT *
FROM appointment_slots
WHERE end_time <= start_time;


-- ------------------------------------------------------------
-- CHECK 7: Kiem tra FK appointments.slot_id
-- ------------------------------------------------------------
SELECT
    CONSTRAINT_NAME,
    TABLE_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'appointments'
  AND COLUMN_NAME = 'slot_id';


-- ------------------------------------------------------------
-- CHECK 8: Kiem tra index cua appointment_slots
-- ------------------------------------------------------------
SHOW INDEX
FROM appointment_slots;


-- ------------------------------------------------------------
-- CHECK 9: EXPLAIN truy van tim slot trong ngay
-- ------------------------------------------------------------
EXPLAIN
SELECT
    id,
    doctor_id,
    slot_date,
    start_time,
    end_time,
    is_available,
    status
FROM appointment_slots
WHERE doctor_id = (
    SELECT id
    FROM doctors
    ORDER BY id
    LIMIT 1
)
  AND slot_date = '2026-10-05'
  AND status = 'AVAILABLE'
  AND is_available = b'1'
ORDER BY start_time;


-- ------------------------------------------------------------
-- CHECK 10: EXPLAIN truy van appointment theo patient
-- ------------------------------------------------------------
EXPLAIN
SELECT
    id,
    patient_id,
    doctor_id,
    slot_id,
    status,
    created_at
FROM appointments
WHERE patient_id = (
    SELECT id
    FROM patients
    ORDER BY id
    LIMIT 1
)
ORDER BY created_at DESC;