-- ============================================================
-- BUOI 5 - V2 DATABASE MIGRATION (FIXED)
-- Chuyen schedules -> appointment_slots
-- Chuyen appointments.schedule_id -> appointments.slot_id
--
-- Nguyen tac:
--   * slot_id la FK den appointment_slots va luon giu lich goc.
--   * active_slot_id la UNIQUE marker cho appointment dang giu slot.
--   * Khi CANCELLED, active_slot_id phai NULL de slot duoc dat lai.
--   * KHONG UNIQUE(slot_id), vi appointment da huy van co the giu slot_id.
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
        FOREIGN KEY (doctor_id) REFERENCES doctors(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_appointment_slots_time
        CHECK (end_time > start_time),
    CONSTRAINT chk_appointment_slots_status
        CHECK (status IN ('AVAILABLE', 'BOOKED', 'CANCELLED'))
);

INSERT INTO appointment_slots
(
    id, doctor_id, slot_date, start_time, end_time,
    is_available, status, note, version, created_at, updated_at
)
SELECT
    s.id,
    s.doctor_id,
    s.schedule_date,
    s.start_time,
    s.end_time,
    CASE WHEN s.status = 'OPEN' THEN b'1' ELSE b'0' END,
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
    SELECT 1 FROM appointment_slots aps WHERE aps.id = s.id
);

ALTER TABLE appointments
    ADD COLUMN slot_id BIGINT UNSIGNED NULL AFTER doctor_id;

UPDATE appointments a
JOIN appointment_slots aps ON aps.id = a.schedule_id
SET a.slot_id = aps.id;

UPDATE appointment_slots aps
JOIN appointments a ON a.slot_id = aps.id
SET aps.is_available = b'0', aps.status = 'BOOKED'
WHERE a.status <> 'CANCELLED';

UPDATE appointment_slots aps
JOIN appointments a ON a.slot_id = aps.id
SET aps.is_available = b'1', aps.status = 'AVAILABLE'
WHERE a.status = 'CANCELLED';

SELECT COUNT(*) AS appointments_without_slot
FROM appointments
WHERE slot_id IS NULL;

-- Xoa FK cu cua schedule_id
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
    @fk_schedule IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE appointments DROP FOREIGN KEY `', @fk_schedule, '`')
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Xoa index schedule_id cu neu co
SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
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

-- Xoa unique cu neu ten uq_active_slot ton tai.
-- Sau do tao UNIQUE(active_slot_id).
SET @sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
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

-- Bo cac cot schedule-specific khong con dung.
-- GIU appointment_code, service_id, cancelled_at va cac cot model hien tai.
ALTER TABLE appointments
    DROP COLUMN schedule_id,
    DROP COLUMN appointment_date,
    DROP COLUMN start_time,
    DROP COLUMN end_time,
    DROP COLUMN reason,
    DROP COLUMN booked_at,
    DROP COLUMN cancellation_reason;

ALTER TABLE appointments
    MODIFY COLUMN slot_id BIGINT UNSIGNED NOT NULL;

ALTER TABLE appointments
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING';

ALTER TABLE appointments
    ADD COLUMN notes TEXT NULL;

-- active_slot_id la UNIQUE marker:
-- NULL = khong giu slot (vi du CANCELLED)
-- slot_id = appointment dang giu slot
ALTER TABLE appointments
    ADD COLUMN active_slot_id BIGINT UNSIGNED NULL;

ALTER TABLE appointments
    MODIFY COLUMN created_at DATETIME(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY COLUMN updated_at DATETIME(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6);

UPDATE appointments
SET active_slot_id = CASE
    WHEN status = 'CANCELLED' THEN NULL
    ELSE slot_id
END;

ALTER TABLE appointments
    ADD UNIQUE KEY uq_appointments_active_slot (active_slot_id),
    ADD CONSTRAINT fk_appointments_slot
        FOREIGN KEY (slot_id)
        REFERENCES appointment_slots(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE;

CREATE INDEX idx_appointments_patient
    ON appointments(patient_id);

CREATE INDEX idx_appointments_doctor
    ON appointments(doctor_id);

DROP TABLE schedules;

SELECT id, doctor_id, slot_date, start_time, end_time,
       is_available, status, note, version
FROM appointment_slots
ORDER BY slot_date, start_time;

SELECT id, patient_id, doctor_id, slot_id, active_slot_id,
       appointment_code, service_id, status, notes,
       cancelled_at, created_at, updated_at
FROM appointments
ORDER BY id;
