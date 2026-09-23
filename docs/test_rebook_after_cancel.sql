-- ============================================================
-- BẰNG CHỨNG: slot có thể đặt lại sau khi hủy
-- Chạy sau schema.sql + seed.sql, trên DB clinic_management.
-- Dùng slot 2026-09-24 09:00-09:30 của bác sĩ BS0001 (đang OPEN, chưa có ai đặt).
-- ============================================================

-- 1) Đặt lịch lần đầu cho bệnh nhân PT0003
INSERT INTO appointments (
    appointment_code, patient_id, doctor_id, schedule_id, service_id,
    appointment_date, start_time, end_time, reason, status
)
SELECT 'APT-TEST-1', p.id, d.id, s.id, sv.id, s.schedule_date, s.start_time, s.end_time,
       'Kiem thu huy va dat lai', 'BOOKED'
FROM patients p, doctors d, schedules s, services sv
WHERE p.patient_code = 'PT0003'
  AND d.doctor_code   = 'BS0001'
  AND s.schedule_date = '2026-09-24'
  AND s.start_time    = '09:00:00'
  AND sv.code          = 'SVC-GEN';

-- Kiem tra: phai co 1 dong, active_schedule_id KHONG NULL
SELECT id, appointment_code, status, schedule_id, active_schedule_id
FROM appointments WHERE appointment_code = 'APT-TEST-1';

-- 2) Huy lich vua dat
UPDATE appointments
SET status = 'CANCELLED', cancelled_at = NOW(), cancellation_reason = 'Kiem thu'
WHERE appointment_code = 'APT-TEST-1';

-- Kiem tra: active_schedule_id phai chuyen thanh NULL
SELECT id, appointment_code, status, schedule_id, active_schedule_id
FROM appointments WHERE appointment_code = 'APT-TEST-1';

-- 3) Dat lai DUNG slot do cho benh nhan khac (PT0001)
-- TRUOC KHI SUA: lenh nay bao loi "Duplicate entry ... for key 'uq_active_slot'"
-- SAU KHI SUA: lenh nay phai INSERT THANH CONG
INSERT INTO appointments (
    appointment_code, patient_id, doctor_id, schedule_id, service_id,
    appointment_date, start_time, end_time, reason, status
)
SELECT 'APT-TEST-2', p.id, d.id, s.id, sv.id, s.schedule_date, s.start_time, s.end_time,
       'Kiem thu dat lai sau khi huy', 'BOOKED'
FROM patients p, doctors d, schedules s, services sv
WHERE p.patient_code = 'PT0001'
  AND d.doctor_code   = 'BS0001'
  AND s.schedule_date = '2026-09-24'
  AND s.start_time    = '09:00:00'
  AND sv.code          = 'SVC-GEN';

-- Ket qua mong doi: 2 dong (APT-TEST-1 = CANCELLED, APT-TEST-2 = BOOKED),
-- cung mot schedule_id nhung chi APT-TEST-2 co active_schedule_id khac NULL.
SELECT id, appointment_code, status, schedule_id, active_schedule_id
FROM appointments WHERE appointment_code IN ('APT-TEST-1','APT-TEST-2');

-- Don dep du lieu kiem thu sau khi da chup anh minh chung (tuy chon)
-- DELETE FROM appointments WHERE appointment_code IN ('APT-TEST-1','APT-TEST-2');
