-- ============================================================
-- MEDBOOK - BUOI 7 - V2: BAO CAO TONG HOP VA CHI MUC
-- ============================================================
-- Vai tro: V2 - Phu trach du lieu
-- Database: medbook_v3_test
--
-- San pham ca nhan:
-- 1. Ba truy van bao cao tong hop
-- 2. Chi muc phuc vu truy van theo thoi gian
-- ============================================================


-- ============================================================
-- REPORT 1: THONG KE SO LICH HEN THEO TRANG THAI
-- THEO KHOANG THOI GIAN
-- ============================================================

SELECT
    a.status,
    COUNT(*) AS total_appointments
FROM appointments a
JOIN appointment_slots s
    ON s.id = a.slot_id
WHERE s.slot_date BETWEEN '2026-10-01' AND '2026-10-31'
GROUP BY a.status
ORDER BY total_appointments DESC;


-- ============================================================
-- REPORT 2: THONG KE SO LICH HEN THEO BAC SI
-- THEO KHOANG THOI GIAN
-- ============================================================

SELECT
    d.id AS doctor_id,
    d.full_name AS doctor_name,
    COUNT(*) AS total_appointments
FROM appointments a
JOIN doctors d
    ON d.id = a.doctor_id
JOIN appointment_slots s
    ON s.id = a.slot_id
WHERE s.slot_date BETWEEN '2026-10-01' AND '2026-10-31'
GROUP BY d.id, d.full_name
ORDER BY total_appointments DESC;


-- ============================================================
-- REPORT 3: TY LE SU DUNG SLOT THEO BAC SI
-- THEO KHOANG THOI GIAN
-- ============================================================

SELECT
    d.id AS doctor_id,
    d.full_name AS doctor_name,
    COUNT(s.id) AS total_slots,
    SUM(
        CASE
            WHEN s.is_available = 0 THEN 1
            ELSE 0
        END
    ) AS booked_slots,
    ROUND(
        100.0 * SUM(
            CASE
                WHEN s.is_available = 0 THEN 1
                ELSE 0
            END
        ) / NULLIF(COUNT(s.id), 0),
        2
    ) AS utilization_rate
FROM appointment_slots s
JOIN doctors d
    ON d.id = s.doctor_id
WHERE s.slot_date BETWEEN '2026-10-01' AND '2026-10-31'
GROUP BY d.id, d.full_name
ORDER BY utilization_rate DESC;


-- ============================================================
-- INDEX PHUC VU TRUY VAN THEO THOI GIAN
-- ============================================================

CREATE INDEX idx_appointment_slots_slot_date
ON appointment_slots(slot_date);


-- ============================================================
-- KIEM TRA INDEX
-- ============================================================

SHOW INDEX FROM appointment_slots;


-- ============================================================
-- GHI CHU KET QUA KIEM THU
-- ============================================================
-- Report 1: PENDING = 3
-- Report 2: Doctor V3 = 3 appointments
-- Report 3: Doctor V3 = 3 slots, 3 booked, utilization = 100.00%
--
-- Median response time cua hot query:
-- 2.27 ms (10 lan do bang SHOW PROFILES)
--
-- So bang co PK + index va cac FK theo quan he:
-- 20/20 bang
-- ============================================================