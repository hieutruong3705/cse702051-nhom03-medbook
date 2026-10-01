-- Roles
INSERT INTO roles (id, code, name, description, created_at) VALUES
(1, 'PATIENT', 'Bệnh nhân', 'Người bệnh đăng ký khám', NOW()),
(2, 'DOCTOR', 'Bác sĩ', 'Bác sĩ khám chữa bệnh', NOW()),
(3, 'ADMIN', 'Quản trị viên', 'Quản trị hệ thống', NOW());

-- Specialties
INSERT INTO specialties (id, code, name, description, status, created_at, updated_at) VALUES
(1, 'NTQ', 'Nội tổng quát', 'Khám các bệnh lý nội khoa chung', 'ACTIVE', NOW(), NOW()),
(2, 'NK', 'Nhi khoa', 'Khám và điều trị bệnh cho trẻ em', 'ACTIVE', NOW(), NOW()),
(3, 'DL', 'Da liễu', 'Khám và điều trị các bệnh về da', 'ACTIVE', NOW(), NOW()),
(4, 'TM', 'Tim mạch', 'Khám và điều trị các bệnh lý tim mạch', 'ACTIVE', NOW(), NOW()),
(5, 'TMH', 'Tai mũi họng', 'Khám và điều trị các bệnh về tai mũi họng', 'ACTIVE', NOW(), NOW());

-- Users
INSERT INTO users (id, username, password_hash, full_name, email, phone, status, created_at, updated_at) VALUES
(1, 'admin1', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Admin', 'admin1@medbook.local', '0901234567', 'ACTIVE', NOW(), NOW()),
(2, 'doctor1', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Bac si Lan', 'doctor1@medbook.local', '0912345678', 'ACTIVE', NOW(), NOW()),
(3, 'doctor2', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Bac si Huy', 'doctor2@medbook.local', '0923456789', 'ACTIVE', NOW(), NOW()),
(4, 'patient1', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Nguyen Minh An', 'patient1@medbook.local', '0934567890', 'ACTIVE', NOW(), NOW()),
(5, 'patient2', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Tran Gia Binh', 'patient2@medbook.local', '0945678901', 'ACTIVE', NOW(), NOW());

-- User Roles
INSERT INTO user_roles (user_id, role_id, created_at) VALUES
(1, 3, NOW()),
(2, 2, NOW()),
(3, 2, NOW()),
(4, 1, NOW()),
(5, 1, NOW());

-- Doctors
INSERT INTO doctors (id, user_id, specialty_id, full_name, phone, license_number, bio, is_active, created_at, updated_at) VALUES
(1, 2, 1, 'Bac si Lan', '0912345678', 'CCHN-0001', 'Bác sĩ chuyên khoa Nội tổng quát', TRUE, NOW(), NOW()),
(2, 3, 2, 'Bac si Huy', '0923456789', 'CCHN-0002', 'Bác sĩ chuyên khoa Nhi khoa', TRUE, NOW(), NOW());

-- Patients
INSERT INTO patients (id, user_id, patient_code, full_name, date_of_birth, gender_code, phone, email, address, emergency_contact_name, emergency_contact_phone, blood_type, allergies, status, created_at, updated_at) VALUES
(1, 4, 'BN000001', 'Nguyen Minh An', '1990-01-01', 'MALE', '0934567890', 'patient1@medbook.local', 'Hà Nội', 'Người thân 1', '0956789012', 'O', 'Không', 'ACTIVE', NOW(), NOW()),
(2, 5, 'BN000002', 'Tran Gia Binh', '1985-05-15', 'MALE', '0945678901', 'patient2@medbook.local', 'Hà Nội', 'Người thân 2', '0967890123', 'A', 'Phấn hoa', 'ACTIVE', NOW(), NOW());

-- Medical Services
INSERT INTO services (id, code, name, description, duration_minutes, price, status, created_at, updated_at) VALUES
(1, 'DV01', 'Khám tổng quát', 'Khám sức khỏe tổng quát', 30, 200000.00, 'ACTIVE', NOW(), NOW()),
(2, 'DV02', 'Khám chuyên khoa', 'Khám chuyên khoa sâu', 45, 300000.00, 'ACTIVE', NOW(), NOW()),
(3, 'DV03', 'Xét nghiệm máu', 'Xét nghiệm máu cơ bản', 15, 150000.00, 'ACTIVE', NOW(), NOW()),
(4, 'DV04', 'Siêu âm', 'Siêu âm ổ bụng', 20, 250000.00, 'ACTIVE', NOW(), NOW());

-- Medicines
INSERT INTO medicines (id, code, name, unit, description, status, created_at, updated_at) VALUES
(1, 'T01', 'Paracetamol 500mg', 'Viên', 'Giảm đau, hạ sốt', 'ACTIVE', NOW(), NOW()),
(2, 'T02', 'Amoxicillin 500mg', 'Viên', 'Kháng sinh', 'ACTIVE', NOW(), NOW()),
(3, 'T03', 'Vitamin C 500mg', 'Viên', 'Tăng sức đề kháng', 'ACTIVE', NOW(), NOW()),
(4, 'T04', 'Loratadine 10mg', 'Viên', 'Thuốc chống dị ứng', 'ACTIVE', NOW(), NOW()),
(5, 'T05', 'Omeprazole 20mg', 'Viên', 'Giảm tiết axit dạ dày', 'ACTIVE', NOW(), NOW());

-- Appointment Slots
INSERT INTO appointment_slots (id, doctor_id, slot_date, start_time, end_time, is_available, status, version, created_at, updated_at) VALUES
(1, 1, CURDATE(), '08:00:00', '08:30:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(2, 1, CURDATE(), '08:30:00', '09:00:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(3, 1, CURDATE(), '09:00:00', '09:30:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(4, 1, CURDATE(), '09:30:00', '10:00:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(5, 1, CURDATE(), '10:00:00', '10:30:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(6, 1, CURDATE(), '14:00:00', '14:30:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(7, 1, CURDATE(), '14:30:00', '15:00:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(8, 1, CURDATE(), '15:00:00', '15:30:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(9, 2, CURDATE(), '08:00:00', '08:30:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(10, 2, CURDATE(), '08:30:00', '09:00:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(11, 2, CURDATE(), '09:00:00', '09:30:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(12, 2, CURDATE(), '14:00:00', '14:30:00', TRUE, 'AVAILABLE', 0, NOW(), NOW()),
(13, 2, CURDATE(), '14:30:00', '15:00:00', TRUE, 'AVAILABLE', 0, NOW(), NOW());

-- Bộ đếm IDENTITY của H2 không tự tăng khi chèn ID cố định ở trên, nên INSERT tiếp theo
-- (đăng ký, đặt lịch, ...) sẽ đụng khóa chính. Đặt lại bộ đếm sau phần seed.
-- Chỉ chạy trên H2 (profile docker/MySQL không nạp data.sql; MySQL tự tăng AUTO_INCREMENT).
ALTER TABLE roles ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE users ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE specialties ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE doctors ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE patients ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE services ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE medicines ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE appointment_slots ALTER COLUMN id RESTART WITH 1000;

-- Ca làm việc mẫu khớp với slot seed (hôm nay) và 3 ngày kế tiếp, để khi chạy demo luôn có giờ trống còn
-- đặt được (slot của hôm nay có thể đã qua giờ). Đặt SAU các lệnh RESTART nên id tự sinh bắt đầu từ 1000.
INSERT INTO doctor_schedules (doctor_id, work_date, start_time, end_time, created_at, updated_at)
SELECT s.doctor_id, DATEADD('DAY', d.n, CURDATE()), s.start_time, s.end_time, NOW(), NOW()
FROM (SELECT 1 AS doctor_id, TIME '08:00:00' AS start_time, TIME '10:30:00' AS end_time
      UNION ALL SELECT 1, TIME '14:00:00', TIME '15:30:00'
      UNION ALL SELECT 2, TIME '08:00:00', TIME '09:30:00'
      UNION ALL SELECT 2, TIME '14:00:00', TIME '15:00:00') s
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) d;

INSERT INTO appointment_slots (doctor_id, slot_date, start_time, end_time, is_available, status, version, created_at, updated_at)
SELECT s.doctor_id, DATEADD('DAY', d.n, s.slot_date), s.start_time, s.end_time, TRUE, 'AVAILABLE', 0, NOW(), NOW()
FROM appointment_slots s
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) d
WHERE s.id BETWEEN 1 AND 13;