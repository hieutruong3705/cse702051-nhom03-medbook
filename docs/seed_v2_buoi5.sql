-- seed_v2_buoi5.sql
-- 5 slot mẫu cho V2 Buổi 05.
-- Không dùng schedule_id. Chạy trên DB sạch hoặc sau khi xóa 5 slot mẫu cũ.

DELETE FROM appointment_slots
WHERE doctor_id = 1
  AND slot_date = '2026-10-05'
  AND start_time IN ('08:00:00','08:30:00','09:00:00','09:30:00','10:00:00');

INSERT INTO appointment_slots
    (doctor_id, slot_date, start_time, end_time, is_available, status, note, version)
VALUES
    (1, '2026-10-05', '08:00:00', '08:30:00', b'1', 'AVAILABLE', 'V2 sample slot 01', 0),
    (1, '2026-10-05', '08:30:00', '09:00:00', b'1', 'AVAILABLE', 'V2 sample slot 02', 0),
    (1, '2026-10-05', '09:00:00', '09:30:00', b'1', 'AVAILABLE', 'V2 sample slot 03', 0),
    (1, '2026-10-05', '09:30:00', '10:00:00', b'1', 'AVAILABLE', 'V2 sample slot 04', 0),
    (1, '2026-10-05', '10:00:00', '10:30:00', b'1', 'AVAILABLE', 'V2 sample slot 05', 0);
