USE medbook_v3_test;

-- ============================================================
-- MEDBOOK V3 - SEED DATA
-- AppointmentSlot / Appointment
-- ============================================================

-- ============================================================
-- 1. ROLES
-- ============================================================

INSERT INTO roles (code, name, description, created_at)
SELECT 'ADMIN', 'Administrator', 'Quan tri he thong', NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE code = 'ADMIN'
);

INSERT INTO roles (code, name, description, created_at)
SELECT 'DOCTOR', 'Doctor', 'Bac si', NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE code = 'DOCTOR'
);

INSERT INTO roles (code, name, description, created_at)
SELECT 'RECEPTIONIST', 'Receptionist', 'Le tan', NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE code = 'RECEPTIONIST'
);

INSERT INTO roles (code, name, description, created_at)
SELECT 'PATIENT', 'Patient', 'Benh nhan', NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE code = 'PATIENT'
);


-- ============================================================
-- 2. USERS
-- ============================================================

INSERT INTO users (
    username,
    email,
    password_hash,
    full_name,
    phone,
    status,
    created_at,
    updated_at
)
SELECT
    'doctor.v3',
    'doctor.v3@medbook.local',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoO8jHqK4x8Vf9KJ6N6Y8J0X9q5nQY0K9W',
    'Doctor V3',
    '0900000001',
    'ACTIVE',
    NOW(6),
    NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'doctor.v3'
);

INSERT INTO users (
    username,
    email,
    password_hash,
    full_name,
    phone,
    status,
    created_at,
    updated_at
)
SELECT
    'patient.v3',
    'patient.v3@medbook.local',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoO8jHqK4x8Vf9KJ6N6Y8J0X9q5nQY0K9W',
    'Patient V3',
    '0900000002',
    'ACTIVE',
    NOW(6),
    NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'patient.v3'
);


-- ============================================================
-- 3. USER ROLES
-- ============================================================

INSERT INTO user_roles (user_id, role_id, created_at)
SELECT
    u.id,
    r.id,
    NOW(6)
FROM users u
JOIN roles r ON r.code = 'DOCTOR'
WHERE u.username = 'doctor.v3'
  AND NOT EXISTS (
      SELECT 1
      FROM user_roles ur
      WHERE ur.user_id = u.id
        AND ur.role_id = r.id
  );

INSERT INTO user_roles (user_id, role_id, created_at)
SELECT
    u.id,
    r.id,
    NOW(6)
FROM users u
JOIN roles r ON r.code = 'PATIENT'
WHERE u.username = 'patient.v3'
  AND NOT EXISTS (
      SELECT 1
      FROM user_roles ur
      WHERE ur.user_id = u.id
        AND ur.role_id = r.id
  );


-- ============================================================
-- 4. PATIENT
-- ============================================================

INSERT INTO patients (
    patient_code,
    user_id,
    full_name,
    date_of_birth,
    gender_code,
    phone,
    email,
    status,
    created_at,
    updated_at
)
SELECT
    'PT-V3-001',
    u.id,
    'Patient V3',
    '2006-03-24',
    'FEMALE',
    '0900000002',
    'patient.v3@medbook.local',
    'ACTIVE',
    NOW(6),
    NOW(6)
FROM users u
WHERE u.username = 'patient.v3'
  AND NOT EXISTS (
      SELECT 1 FROM patients WHERE patient_code = 'PT-V3-001'
  );


-- ============================================================
-- 5. SPECIALTY
-- ============================================================

INSERT INTO specialties (
    code,
    name,
    description,
    status,
    created_at,
    updated_at
)
SELECT
    'V3-GEN',
    'General Medicine',
    'Chuyen khoa noi tong quat - V3',
    'ACTIVE',
    NOW(6),
    NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM specialties WHERE code = 'V3-GEN'
);


-- ============================================================
-- 6. DOCTOR
-- ============================================================

INSERT INTO doctors (
    user_id,
    full_name,
    phone,
    license_number,
    bio,
    is_active,
    specialty_id,
    created_at,
    updated_at
)
SELECT
    u.id,
    'Doctor V3',
    '0900000001',
    'LIC-V3-001',
    'Doctor test for MedBook V3',
    b'1',
    s.id,
    NOW(6),
    NOW(6)
FROM users u
JOIN specialties s ON s.code = 'V3-GEN'
WHERE u.username = 'doctor.v3'
  AND NOT EXISTS (
      SELECT 1 FROM doctors WHERE user_id = u.id
  );


-- ============================================================
-- 7. APPOINTMENT SLOTS
-- ============================================================

INSERT INTO appointment_slots (
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
    d.id,
    '2026-10-05',
    '08:00:00',
    '08:30:00',
    b'1',
    'AVAILABLE',
    'V3 test slot 01',
    0,
    NOW(6),
    NOW(6)
FROM doctors d
JOIN users u ON u.id = d.user_id
WHERE u.username = 'doctor.v3'
  AND NOT EXISTS (
      SELECT 1
      FROM appointment_slots s
      WHERE s.doctor_id = d.id
        AND s.slot_date = '2026-10-05'
        AND s.start_time = '08:00:00'
        AND s.end_time = '08:30:00'
  );


INSERT INTO appointment_slots (
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
    d.id,
    '2026-10-05',
    '08:30:00',
    '09:00:00',
    b'1',
    'AVAILABLE',
    'V3 test slot 02',
    0,
    NOW(6),
    NOW(6)
FROM doctors d
JOIN users u ON u.id = d.user_id
WHERE u.username = 'doctor.v3'
  AND NOT EXISTS (
      SELECT 1
      FROM appointment_slots s
      WHERE s.doctor_id = d.id
        AND s.slot_date = '2026-10-05'
        AND s.start_time = '08:30:00'
        AND s.end_time = '09:00:00'
  );


INSERT INTO appointment_slots (
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
    d.id,
    '2026-10-05',
    '09:00:00',
    '09:30:00',
    b'1',
    'AVAILABLE',
    'V3 test slot 03 - API booking',
    0,
    NOW(6),
    NOW(6)
FROM doctors d
JOIN users u ON u.id = d.user_id
WHERE u.username = 'doctor.v3'
  AND NOT EXISTS (
      SELECT 1
      FROM appointment_slots s
      WHERE s.doctor_id = d.id
        AND s.slot_date = '2026-10-05'
        AND s.start_time = '09:00:00'
        AND s.end_time = '09:30:00'
  );


-- ============================================================
-- 8. APPOINTMENTS
-- ============================================================
-- Khong tao appointment san.
-- De slot 09:00-09:30 duoc dung de test API booking.
