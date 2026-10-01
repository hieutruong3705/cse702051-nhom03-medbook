-- ============================================================
-- BUOI 5 - V2 DATABASE MIGRATION
-- Chuyen:
--   schedules -> appointment_slots
--   appointments.schedule_id -> appointments.slot_id
--
-- Doi chieu theo Java V3:
-- Appointment:
--   patient_id, doctor_id, slot_id, status, notes,
--   created_at, updated_at
--
-- AppointmentSlot:
--   doctor_id, slot_date, start_time, end_time,
--   is_available, status, note, version,
--   created_at, updated_at
--
-- LUU Y:
-- Khong hard-code USE medbook_db.
-- Database duoc chon boi lenh mysql khi chay file.
-- ============================================================


-- ============================================================
-- 1. TAO BANG appointment_slots
-- ============================================================

CREATE TABLE IF NOT EXISTS appointment_slots (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    doctor_id BIGINT UNSIGNED NOT NULL,
    slot_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,

    is_available BIT(1) NOT NULL DEFAULT b'1',
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    note VARCHAR(255) NULL,

    version BIGINT NOT NULL DEFAULT 0,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    KEY idx_appointment_slots_doctor (doctor_id),
    KEY idx_appointment_slots_date (slot_date),
    KEY idx_appointment_slots_booking
        (doctor_id, slot_date, status, is_available),

    CONSTRAINT fk_appointment_slots_doctor
        FOREIGN KEY (doctor_id)
        REFERENCES doctors(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT chk_appointment_slots_time
        CHECK (end_time > start_time),

    CONSTRAINT chk_appointment_slots_status
        CHECK (status IN ('AVAILABLE', 'BOOKED', 'CANCELLED'))
);


-- ============================================================
-- 2. MIGRATE schedules -> appointment_slots
-- ============================================================

INSERT INTO appointment_slots
(
    id,
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
SELECT
    s.id,
    s.doctor_id,
    s.schedule_date,
    s.start_time,
    s.end_time,

    CASE
        WHEN s.status = 'OPEN' THEN b'1'
        ELSE b'0'
    END,

    CASE
        WHEN s.status = 'OPEN' THEN 'AVAILABLE'
        WHEN s.status = 'CANCELLED' THEN 'CANCELLED'
        WHEN s.status = 'BOOKED' THEN 'BOOKED'
        ELSE 'AVAILABLE'
    END,

    s.note,
    0,
    s.created_at,
    s.updated_at

FROM schedules s
WHERE NOT EXISTS (
    SELECT 1
    FROM appointment_slots aps
    WHERE aps.id = s.id
);


-- ============================================================
-- 3. THEM appointments.slot_id
-- ============================================================

ALTER TABLE appointments
    ADD COLUMN slot_id BIGINT UNSIGNED NULL AFTER doctor_id;


-- ============================================================
-- 4. CHUYEN schedules -> slot_id
-- ============================================================

UPDATE appointments a
JOIN appointment_slots aps
    ON aps.id = a.schedule_id
SET a.slot_id = aps.id;


-- ============================================================
-- 5. DONG BO TRANG THAI SLOT
-- ============================================================

UPDATE appointment_slots aps
JOIN appointments a
    ON a.slot_id = aps.id
SET
    aps.is_available = b'0',
    aps.status = 'BOOKED'
WHERE a.status <> 'CANCELLED';


UPDATE appointment_slots aps
JOIN appointments a
    ON a.slot_id = aps.id
SET
    aps.is_available = b'1',
    aps.status = 'AVAILABLE'
WHERE a.status = 'CANCELLED';


-- ============================================================
-- 6. KIEM TRA APPOINTMENT CO SLOT HAY KHONG
-- ============================================================

SELECT
    COUNT(*) AS appointments_without_slot
FROM appointments
WHERE slot_id IS NULL;


-- ============================================================
-- 7. XOA FK CU CUA schedule_id
-- ============================================================

SET @fk_schedule = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'appointments'
      AND COLUMN_NAME = 'schedule_id'
      AND REFERENCED_TABLE_NAME = 'schedules'
    LIMIT 1
);

SET @sql = IF(
    @fk_schedule IS NULL,
    'SELECT 1',
    CONCAT(
        'ALTER TABLE appointments DROP FOREIGN KEY `',
        @fk_schedule,
        '`'
    )
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================
-- 8. XOA FK CU CUA service_id
-- ============================================================

SET @fk_service = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'appointments'
      AND COLUMN_NAME = 'service_id'
      AND REFERENCED_TABLE_NAME = 'services'
    LIMIT 1
);

SET @sql = IF(
    @fk_service IS NULL,
    'SELECT 1',
    CONCAT(
        'ALTER TABLE appointments DROP FOREIGN KEY `',
        @fk_service,
        '`'
    )
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================
-- 9. XOA INDEX CU
-- ============================================================

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'appointments'
          AND INDEX_NAME = 'uq_active_slot'
    ),
    'ALTER TABLE appointments DROP INDEX uq_active_slot',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'appointments'
          AND INDEX_NAME = 'schedule_id'
    ),
    'ALTER TABLE appointments DROP INDEX schedule_id',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================
-- 10. XOA CAC COT CU KHONG CON DUNG
-- ============================================================

ALTER TABLE appointments
    DROP COLUMN schedule_id,
    DROP COLUMN service_id,
    DROP COLUMN appointment_date,
    DROP COLUMN start_time,
    DROP COLUMN end_time,
    DROP COLUMN reason,
    DROP COLUMN appointment_code,
    DROP COLUMN booked_at,
    DROP COLUMN cancelled_at,
    DROP COLUMN cancellation_reason;


-- ============================================================
-- 11. DONG BO KIEU DU LIEU VA TRANG THAI
-- ============================================================

ALTER TABLE appointments
    MODIFY COLUMN slot_id BIGINT UNSIGNED NOT NULL;

ALTER TABLE appointments
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING';


-- ============================================================
-- 12. THEM notes THEO JAVA V3
-- ============================================================

ALTER TABLE appointments
    ADD COLUMN notes TEXT NULL;


-- ============================================================
-- 13. DONG BO TIMESTAMP
-- ============================================================

ALTER TABLE appointments
    MODIFY COLUMN created_at DATETIME(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    MODIFY COLUMN updated_at DATETIME(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6);


-- ============================================================
-- 14. TAO UNIQUE SLOT + FK MOI
-- ============================================================

ALTER TABLE appointments
    ADD UNIQUE KEY uk_appointments_slot (slot_id),

    ADD CONSTRAINT fk_appointments_slot
        FOREIGN KEY (slot_id)
        REFERENCES appointment_slots(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE;


-- ============================================================
-- 15. INDEX CHO APPOINTMENTS
-- ============================================================

CREATE INDEX idx_appointments_patient
    ON appointments(patient_id);

CREATE INDEX idx_appointments_doctor
    ON appointments(doctor_id);


-- ============================================================
-- 16. XOA BANG schedules CU
-- ============================================================

DROP TABLE schedules;


-- ============================================================
-- 17. KIEM TRA SAU MIGRATION
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