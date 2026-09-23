-- ============================================================
-- INTEGRITY / FK CHECKS | ĐỀ TÀI 03
-- Kỳ vọng các truy vấn FK bên dưới trả về 0 dòng.
-- ============================================================

-- 1. appointment -> patient
SELECT a.id, a.appointment_code
FROM appointments a
LEFT JOIN patients p ON p.id = a.patient_id
WHERE p.id IS NULL;

-- 2. appointment -> doctor
SELECT a.id, a.appointment_code
FROM appointments a
LEFT JOIN doctors d ON d.id = a.doctor_id
WHERE d.id IS NULL;

-- 3. appointment -> schedule
SELECT a.id, a.appointment_code
FROM appointments a
LEFT JOIN schedules s ON s.id = a.schedule_id
WHERE s.id IS NULL;

-- 4. appointment -> service
SELECT a.id, a.appointment_code
FROM appointments a
LEFT JOIN services s ON s.id = a.service_id
WHERE s.id IS NULL;

-- 5. medical_record -> patient
SELECT mr.id, mr.record_code
FROM medical_records mr
LEFT JOIN patients p ON p.id = mr.patient_id
WHERE p.id IS NULL;

-- 6. encounter -> medical_record
SELECT e.id
FROM encounters e
LEFT JOIN medical_records mr ON mr.id = e.medical_record_id
WHERE mr.id IS NULL;

-- 7. encounter -> doctor
SELECT e.id
FROM encounters e
LEFT JOIN doctors d ON d.id = e.doctor_id
WHERE d.id IS NULL;

-- 8. prescription -> encounter
SELECT pr.id, pr.prescription_code
FROM prescriptions pr
LEFT JOIN encounters e ON e.id = pr.encounter_id
WHERE e.id IS NULL;

-- 9. invoice -> patient
SELECT i.id, i.invoice_code
FROM invoices i
LEFT JOIN patients p ON p.id = i.patient_id
WHERE p.id IS NULL;

-- 10. invoice_item -> invoice
SELECT ii.id
FROM invoice_items ii
LEFT JOIN invoices i ON i.id = ii.invoice_id
WHERE i.id IS NULL;

-- 11. kiểm tra appointment không vượt slot
SELECT a.appointment_code, a.start_time, a.end_time,
       s.start_time AS schedule_start, s.end_time AS schedule_end
FROM appointments a
JOIN schedules s ON s.id=a.schedule_id
WHERE a.start_time < s.start_time
   OR a.end_time > s.end_time;

-- 12. kiểm tra một schedule có nhiều appointment
SELECT schedule_id, COUNT(*) AS appointment_count
FROM appointments
GROUP BY schedule_id
HAVING COUNT(*) > 1;

-- 13. kiểm tra invoice tổng có khớp tổng item
SELECT i.id, i.invoice_code, i.total_amount,
       COALESCE(SUM(ii.line_total),0) AS item_total
FROM invoices i
LEFT JOIN invoice_items ii ON ii.invoice_id=i.id
GROUP BY i.id, i.invoice_code, i.total_amount
HAVING i.total_amount <> COALESCE(SUM(ii.line_total),0);

-- 14. kiểm tra một slot không có từ hai lịch hẹn ĐANG HIỆU LỰC trở lên
-- (appointment đã CANCELLED/NO_SHOW không tính, vì active_schedule_id của chúng là NULL)
-- Kỳ vọng: 0 dòng.
SELECT a1.schedule_id
FROM appointments a1
JOIN appointments a2
  ON a1.schedule_id = a2.schedule_id AND a1.id <> a2.id
WHERE a1.status NOT IN ('CANCELLED','NO_SHOW')
  AND a2.status NOT IN ('CANCELLED','NO_SHOW');
