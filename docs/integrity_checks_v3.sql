USE medbook_db;

-- ============================================================
-- MEDBOOK V3 - INTEGRITY CHECKS
-- Appointment Slot / Appointment
-- ============================================================


-- ============================================================
-- CHECK 1: Kiểm tra orphan appointment
-- appointments.slot_id phải tồn tại trong appointment_slots.id
-- Kết quả mong đợi: 0 dòng
-- ============================================================

SELECT
    a.id AS appointment_id,
    a.slot_id
FROM appointments a
LEFT JOIN appointment_slots s
    ON s.id = a.slot_id
WHERE s.id IS NULL;


-- ============================================================
-- CHECK 2: Kiểm tra doctor của appointment và slot phải giống nhau
-- Kết quả mong đợi: 0 dòng
-- ============================================================

SELECT
    a.id AS appointment_id,
    a.doctor_id AS appointment_doctor_id,
    s.doctor_id AS slot_doctor_id,
    a.slot_id
FROM appointments a
JOIN appointment_slots s
    ON s.id = a.slot_id
WHERE a.doctor_id <> s.doctor_id;


-- ============================================================
-- CHECK 3: Kiểm tra một slot không bị đặt nhiều lần
-- Migration đã có UNIQUE(slot_id)
-- Kết quả mong đợi: 0 dòng
-- ============================================================

SELECT
    slot_id,
    COUNT(*) AS appointment_count
FROM appointments
GROUP BY slot_id
HAVING COUNT(*) > 1;


-- ============================================================
-- CHECK 4: Kiểm tra slot có thời gian hợp lệ
-- start_time phải nhỏ hơn end_time
-- Kết quả mong đợi: 0 dòng
-- ============================================================

SELECT
    id,
    doctor_id,
    slot_date,
    start_time,
    end_time
FROM appointment_slots
WHERE start_time >= end_time;


-- ============================================================
-- CHECK 5: Kiểm tra appointment không được NULL dữ liệu bắt buộc
-- Kết quả mong đợi: 0 dòng
-- ============================================================

SELECT
    id,
    patient_id,
    doctor_id,
    slot_id,
    status
FROM appointments
WHERE patient_id IS NULL
   OR doctor_id IS NULL
   OR slot_id IS NULL
   OR status IS NULL;


-- ============================================================
-- CHECK 6: Kiểm tra version của appointment_slots
-- Optimistic locking version không được âm
-- Kết quả mong đợi: 0 dòng
-- ============================================================

SELECT
    id,
    version
FROM appointment_slots
WHERE version < 0;


-- ============================================================
-- CHECK 7: Kiểm tra slot đã có appointment nhưng vẫn available
-- Theo logic đặt lịch: slot đã đặt phải is_available = 0
-- Kết quả mong đợi: 0 dòng
-- ============================================================

SELECT
    a.id AS appointment_id,
    a.slot_id,
    s.is_available,
    s.status
FROM appointments a
JOIN appointment_slots s
    ON s.id = a.slot_id
WHERE s.is_available <> b'0';


-- ============================================================
-- CHECK 8: Kiểm tra appointment status
-- PENDING là trạng thái mặc định trong Java.
-- Các giá trị thực tế khác được kiểm tra theo enum Java khi chạy.
-- ============================================================

SELECT
    status,
    COUNT(*) AS total
FROM appointments
GROUP BY status
ORDER BY status;


-- ============================================================
-- CHECK 9: Kiểm tra seed V3
-- Phải có appointment và slot liên kết.
-- ============================================================

SELECT
    a.id AS appointment_id,
    a.patient_id,
    a.doctor_id,
    a.slot_id,
    s.slot_date,
    s.start_time,
    s.end_time,
    s.is_available,
    a.status
FROM appointments a
JOIN appointment_slots s
    ON s.id = a.slot_id
ORDER BY a.id;


-- ============================================================
-- CHECK 10: Thống kê V3
-- ============================================================

SELECT
    (SELECT COUNT(*) FROM appointment_slots) AS total_slots,
    (SELECT COUNT(*) FROM appointment_slots WHERE is_available = b'1')
        AS available_slots,
    (SELECT COUNT(*) FROM appointment_slots WHERE is_available = b'0')
        AS booked_slots,
    (SELECT COUNT(*) FROM appointments)
        AS total_appointments;