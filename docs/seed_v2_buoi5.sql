-- ============================================================
-- BUOI 5 - V2 DATABASE SEED
--
-- LUU Y:
-- Khong hard-code USE medbook_db.
-- Database duoc chon boi lenh mysql khi chay file.
-- ============================================================


-- ============================================================
-- 1. LAY DOCTOR CO SAN
-- ============================================================

SET @doctor_id = (
    SELECT id
    FROM doctors
    ORDER BY id
    LIMIT 1
);


-- ============================================================
-- 2. KIEM TRA DOCTOR
-- ============================================================

SELECT
    @doctor_id AS doctor_id_for_seed;


-- ============================================================
-- 3. TAO CAC APPOINTMENT SLOTS MAU
-- ============================================================

INSERT INTO appointment_slots
(
    doctor_id,
    slot_date,
    start_time,
    end_time,
    is_available,
    status,
    note,
    version,
    created_at,
    updated_at
)
VALUES
(
    @doctor_id,
    '2026-10-05',
    '08:00:00',
    '08:30:00',
    b'1',
    'AVAILABLE',
    'Buoi 5 - slot test 01',
    0,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
),
(
    @doctor_id,
    '2026-10-05',
    '08:30:00',
    '09:00:00',
    b'1',
    'AVAILABLE',
    'Buoi 5 - slot test 02',
    0,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
),
(
    @doctor_id,
    '2026-10-05',
    '09:00:00',
    '09:30:00',
    b'1',
    'AVAILABLE',
    'Buoi 5 - slot API booking',
    0,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
),
(
    @doctor_id,
    '2026-10-05',
    '09:30:00',
    '10:00:00',
    b'1',
    'AVAILABLE',
    'Buoi 5 - slot test 04',
    0,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
),
(
    @doctor_id,
    '2026-10-05',
    '10:00:00',
    '10:30:00',
    b'1',
    'AVAILABLE',
    'Buoi 5 - slot test 05',
    0,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);


-- ============================================================
-- 4. KIEM TRA SLOT SAU KHI SEED
-- ============================================================

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