-- =====================================================================
-- MedBook - dữ liệu demo cho MySQL (hồ sơ docker, khi MEDBOOK_SEED_DEMO=true và CSDL còn trống).
-- Do DemoSeedRunner nạp, KHÔNG phải migration Flyway và không bao giờ chạy ở hồ sơ prod.
--
-- Năm tài khoản demo dùng chung mật khẩu MedBook@2026 (băm BCrypt cost 12), giống data-h2.sql của chế độ phát
-- triển. Chỉ dùng hàm có ở cả MySQL lẫn H2 (CURDATE, TIMESTAMPADD) để bộ test chạy được tệp này.
-- =====================================================================

INSERT INTO roles (id, code, name, description, created_at) VALUES
(1, 'PATIENT', 'Bệnh nhân', 'Người bệnh đăng ký khám', NOW()),
(2, 'DOCTOR', 'Bác sĩ', 'Bác sĩ khám chữa bệnh', NOW()),
(3, 'ADMIN', 'Quản trị viên', 'Quản trị hệ thống', NOW());

INSERT INTO specialties (id, code, name, description, status, created_at, updated_at) VALUES
(1, 'NTQ', 'Nội tổng quát', 'Khám các bệnh lý nội khoa chung', 'ACTIVE', NOW(), NOW()),
(2, 'NK', 'Nhi khoa', 'Khám và điều trị bệnh cho trẻ em', 'ACTIVE', NOW(), NOW()),
(3, 'DL', 'Da liễu', 'Khám và điều trị các bệnh về da', 'ACTIVE', NOW(), NOW()),
(4, 'TM', 'Tim mạch', 'Khám và điều trị các bệnh lý tim mạch', 'ACTIVE', NOW(), NOW()),
(5, 'TMH', 'Tai mũi họng', 'Khám và điều trị các bệnh về tai mũi họng', 'ACTIVE', NOW(), NOW());

INSERT INTO users (id, username, password_hash, full_name, email, phone, status, failed_login_count, token_version, created_at, updated_at) VALUES
(1, 'admin1', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Lê Quản Trị', 'admin1@medbook.local', '0901234567', 'ACTIVE', 0, 0, NOW(), NOW()),
(2, 'doctor1', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Bác sĩ Lan', 'doctor1@medbook.local', '0912345678', 'ACTIVE', 0, 0, NOW(), NOW()),
(3, 'doctor2', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Bác sĩ Huy', 'doctor2@medbook.local', '0923456789', 'ACTIVE', 0, 0, NOW(), NOW()),
(4, 'patient1', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Nguyễn Minh An', 'patient1@medbook.local', '0934567890', 'ACTIVE', 0, 0, NOW(), NOW()),
(5, 'patient2', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Trần Gia Bình', 'patient2@medbook.local', '0945678901', 'ACTIVE', 0, 0, NOW(), NOW());

INSERT INTO user_roles (user_id, role_id, created_at) VALUES
(1, 3, NOW()),
(2, 2, NOW()),
(3, 2, NOW()),
(4, 1, NOW()),
(5, 1, NOW());

INSERT INTO doctors (id, user_id, specialty_id, full_name, phone, license_number, bio, is_active, created_at, updated_at) VALUES
(1, 2, 1, 'Bác sĩ Lan', '0912345678', 'CCHN-0001', 'Bác sĩ chuyên khoa Nội tổng quát', TRUE, NOW(), NOW()),
(2, 3, 2, 'Bác sĩ Huy', '0923456789', 'CCHN-0002', 'Bác sĩ chuyên khoa Nhi khoa', TRUE, NOW(), NOW());

INSERT INTO patients (id, user_id, patient_code, full_name, date_of_birth, gender_code, phone, email, address, emergency_contact_name, emergency_contact_phone, blood_type, allergies, status, created_at, updated_at) VALUES
(1, 4, 'BN000001', 'Nguyễn Minh An', '1990-01-01', 'MALE', '0934567890', 'patient1@medbook.local', 'Hà Nội', 'Người thân 1', '0956789012', 'O', 'Không', 'ACTIVE', NOW(), NOW()),
(2, 5, 'BN000002', 'Trần Gia Bình', '1985-05-15', 'MALE', '0945678901', 'patient2@medbook.local', 'Hà Nội', 'Người thân 2', '0967890123', 'A', 'Phấn hoa', 'ACTIVE', NOW(), NOW());

INSERT INTO services (id, code, name, description, duration_minutes, price, status, created_at, updated_at) VALUES
(1, 'DV01', 'Khám tổng quát', 'Khám sức khỏe tổng quát', 30, 200000.00, 'ACTIVE', NOW(), NOW()),
(2, 'DV02', 'Khám chuyên khoa', 'Khám chuyên khoa sâu', 45, 300000.00, 'ACTIVE', NOW(), NOW()),
(3, 'DV03', 'Xét nghiệm máu', 'Xét nghiệm máu cơ bản', 15, 150000.00, 'ACTIVE', NOW(), NOW()),
(4, 'DV04', 'Siêu âm', 'Siêu âm ổ bụng', 20, 250000.00, 'ACTIVE', NOW(), NOW());

INSERT INTO medicines (id, code, name, unit, description, status, created_at, updated_at) VALUES
(1, 'T01', 'Paracetamol 500mg', 'Viên', 'Giảm đau, hạ sốt', 'ACTIVE', NOW(), NOW()),
(2, 'T02', 'Amoxicillin 500mg', 'Viên', 'Kháng sinh', 'ACTIVE', NOW(), NOW()),
(3, 'T03', 'Vitamin C 500mg', 'Viên', 'Tăng sức đề kháng', 'ACTIVE', NOW(), NOW()),
(4, 'T04', 'Loratadine 10mg', 'Viên', 'Thuốc chống dị ứng', 'ACTIVE', NOW(), NOW()),
(5, 'T05', 'Omeprazole 20mg', 'Viên', 'Giảm tiết axit dạ dày', 'ACTIVE', NOW(), NOW());

-- Ca làm việc trong 7 ngày kể từ ngày mai (để lúc nào chạy demo cũng còn giờ trống đặt được):
-- bác sĩ 1 sáng 08:00-10:00 và chiều 14:00-15:30, bác sĩ 2 sáng 08:00-09:30; mỗi slot 30 phút.
INSERT INTO doctor_schedules (doctor_id, work_date, start_time, end_time, slot_minutes, created_at, updated_at)
SELECT s.doctor_id, TIMESTAMPADD(DAY, d.n, CURDATE()), s.start_time, s.end_time, 30, NOW(), NOW()
FROM (SELECT 1 AS doctor_id, '08:00:00' AS start_time, '10:00:00' AS end_time
      UNION ALL SELECT 1, '14:00:00', '15:30:00'
      UNION ALL SELECT 2, '08:00:00', '09:30:00') s
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) d;

INSERT INTO appointment_slots (doctor_id, slot_date, start_time, end_time, is_available, status, version, created_at, updated_at)
SELECT t.doctor_id, TIMESTAMPADD(DAY, d.n, CURDATE()), t.start_time, t.end_time, TRUE, 'AVAILABLE', 0, NOW(), NOW()
FROM (SELECT 1 AS doctor_id, '08:00:00' AS start_time, '08:30:00' AS end_time
      UNION ALL SELECT 1, '08:30:00', '09:00:00'
      UNION ALL SELECT 1, '09:00:00', '09:30:00'
      UNION ALL SELECT 1, '09:30:00', '10:00:00'
      UNION ALL SELECT 1, '14:00:00', '14:30:00'
      UNION ALL SELECT 1, '14:30:00', '15:00:00'
      UNION ALL SELECT 1, '15:00:00', '15:30:00'
      UNION ALL SELECT 2, '08:00:00', '08:30:00'
      UNION ALL SELECT 2, '08:30:00', '09:00:00'
      UNION ALL SELECT 2, '09:00:00', '09:30:00') t
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) d;
