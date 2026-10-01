-- ============================================================
-- BUOI 5 - V2 INTEGRITY CHECKS
--
-- LUU Y:
-- Khong hard-code USE medbook_db.
-- Database duoc chon boi lenh mysql khi chay file.
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
    SUM(status = 'CANCELLED' AND is_available = b'1')
        AS cancelled_slots
FROM appointment_slots;


-- ------------------------------------------------------------
-- CHECK 2: Slot khong co doctor
-- ------------------------------------------------------------
SELECT
    aps.id,
    aps.doctor_id
FROM appointment_slots aps
LEFT JOIN doctors d
    ON d.id = aps.doctor_id
WHERE d.id IS NULL;


-- ------------------------------------------------------------
-- CHECK 3: Appointment khong co slot
-- ------------------------------------------------------------
SELECT
    id,
    patient_id,
    doctor_id,
    slot_id,
    status
FROM appointments
WHERE slot_id IS NULL;


-- ------------------------------------------------------------
-- CHECK 4: Trung slot trong appointments
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
    id,
    is_available,
    status
FROM appointment_slots
WHERE
       (status = 'AVAILABLE' AND is_available <> b'1')
    OR (status = 'BOOKED' AND is_available <> b'0')
    OR (status = 'CANCELLED' AND is_available NOT IN (b'0', b'1'));


-- ------------------------------------------------------------
-- CHECK 6: Thoi gian slot khong hop le
-- ------------------------------------------------------------
SELECT
    id,
    slot_date,
    start_time,
    end_time
FROM appointment_slots
WHERE end_time <= start_time;


-- ------------------------------------------------------------
-- CHECK 7: Kiem tra FK appointments.slot_id
-- ------------------------------------------------------------
SELECT
    a.id AS appointment_id,
    a.slot_id
FROM appointments a
LEFT JOIN appointment_slots aps
    ON aps.id = a.slot_id
WHERE aps.id IS NULL;


-- ------------------------------------------------------------
-- CHECK 8: Kiem tra index appointment_slots
-- ------------------------------------------------------------
SHOW INDEX FROM appointment_slots;


-- ------------------------------------------------------------
-- CHECK 9: EXPLAIN truy van tim slot trong lich bac si
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


-- ------------------------------------------------------------
-- CHECK 11: Xem danh sach slot sau seed
-- ------------------------------------------------------------
SELECT
    id,
    doctor_id,
    slot_date,
    start_time,
    end_time,
    is_available,
    status,
    note,
    version
FROM appointment_slots
ORDER BY slot_date, start_time;


-- ------------------------------------------------------------
-- CHECK 12: Xem appointment hien tai
-- ------------------------------------------------------------
SELECT
    id,
    patient_id,
    doctor_id,
    slot_id,
    status,
    notes,
    created_at,
    updated_at
FROM appointments
ORDER BY id;