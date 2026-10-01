USE medbook_db;

-- ============================================================
-- MEDBOOK V3 - APPOINTMENT SLOT / APPOINTMENT
-- Migration
-- ============================================================

-- 1. Appointment slots
CREATE TABLE IF NOT EXISTS appointment_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    doctor_id BIGINT NOT NULL,
    slot_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_available BIT(1) NOT NULL DEFAULT b'1',
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    note VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_appointment_slots_doctor
        FOREIGN KEY (doctor_id)
        REFERENCES doctors(id)
);

-- 2. Appointments
CREATE TABLE IF NOT EXISTS appointments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    notes TEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_appointments_slot
        UNIQUE (slot_id),

    CONSTRAINT fk_appointments_patient
        FOREIGN KEY (patient_id)
        REFERENCES patients(id),

    CONSTRAINT fk_appointments_doctor
        FOREIGN KEY (doctor_id)
        REFERENCES doctors(id),

    CONSTRAINT fk_appointments_slot
        FOREIGN KEY (slot_id)
        REFERENCES appointment_slots(id)
);