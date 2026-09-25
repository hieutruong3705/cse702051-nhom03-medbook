-- ============================================================
-- SEED DATA | ĐỀ TÀI 03
-- Tất cả dữ liệu người/bệnh án bên dưới là DỮ LIỆU GIẢ LẬP.
-- Có thể chạy sau schema.sql.
-- Password hash chỉ là chuỗi mẫu phục vụ DB demo; ứng dụng thật
-- phải tạo hash bằng password_hash()/bcrypt/Argon2 ở tầng ứng dụng.
-- ============================================================

SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO roles (code, name, description) VALUES
('ADMIN', 'Quản trị hệ thống', 'Quản trị tài khoản và cấu hình hệ thống'),
('DOCTOR', 'Bác sĩ', 'Khám bệnh và xem bệnh án bệnh nhân mình điều trị'),
('RECEPTIONIST', 'Lễ tân/Điều dưỡng', 'Tiếp nhận và quản lý lịch hẹn'),
('PATIENT', 'Bệnh nhân', 'Đặt lịch và xem bệnh án của chính mình');

INSERT INTO users (username, password_hash, full_name, email, phone, status) VALUES
('admin.demo', '$2b$12$PnFrkpYBSPLJS4NCk2ZZu.AlcTnJFYXgSX6jyNHkPyK0ET5GxcaCS', 'Quản trị viên Demo', 'admin.demo@example.test', '0900000001', 'ACTIVE'),
('doctor.lan', '$2b$12$0rcodhLvHgsSoT/IllkmPek5nuSlF4GUWXFPXIUagRu7juxzErT66', 'BS Nguyễn Minh Lan', 'doctor.lan@example.test', '0900000002', 'ACTIVE'),
('doctor.huy', '$2b$12$0rcodhLvHgsSoT/IllkmPek5nuSlF4GUWXFPXIUagRu7juxzErT66', 'BS Trần Quốc Huy', 'doctor.huy@example.test', '0900000003', 'ACTIVE'),
('reception.demo', '$2b$12$0rcodhLvHgsSoT/IllkmPek5nuSlF4GUWXFPXIUagRu7juxzErT66', 'Lễ tân Demo', 'reception@example.test', '0900000004', 'ACTIVE'),
('patient.an', '$2b$12$FvkFRFLklInWoroIZaKmc.FHL0fgl/SdNuXn3St4h31DcwXp4CVrO', 'Nguyễn Minh An', 'patient.an@example.test', '0900000011', 'ACTIVE'),
('patient.binh', '$2b$12$FvkFRFLklInWoroIZaKmc.FHL0fgl/SdNuXn3St4h31DcwXp4CVrO', 'Trần Gia Bình', 'patient.binh@example.test', '0900000012', 'ACTIVE'),
('patient.chi', '$2b$12$FvkFRFLklInWoroIZaKmc.FHL0fgl/SdNuXn3St4h31DcwXp4CVrO', 'Lê Ngọc Chi', 'patient.chi@example.test', '0900000013', 'ACTIVE');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE u.username='admin.demo' AND r.code='ADMIN';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE u.username='doctor.lan' AND r.code='DOCTOR';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE u.username='doctor.huy' AND r.code='DOCTOR';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE u.username='reception.demo' AND r.code='RECEPTIONIST';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE u.username IN ('patient.an','patient.binh','patient.chi') AND r.code='PATIENT';

INSERT INTO patients (
    user_id, patient_code, full_name, date_of_birth, gender_code, phone, email,
    address, emergency_contact_name, emergency_contact_phone, blood_type, allergies, status
)
SELECT u.id, 'PT0001', 'Nguyễn Minh An', '2004-03-12', 'MALE', '0900000011',
       'patient.an@example.test', 'Hà Nội', 'Nguyễn Văn A', '0900000091',
       'O+', 'Không ghi nhận dị ứng', 'ACTIVE'
FROM users u WHERE u.username='patient.an';

INSERT INTO patients (
    user_id, patient_code, full_name, date_of_birth, gender_code, phone, email,
    address, emergency_contact_name, emergency_contact_phone, blood_type, allergies, status
)
SELECT u.id, 'PT0002', 'Trần Gia Bình', '2003-08-21', 'MALE', '0900000012',
       'patient.binh@example.test', 'Hà Nội', 'Trần Văn B', '0900000092',
       'A+', 'Dị ứng giả lập: không dùng penicillin', 'ACTIVE'
FROM users u WHERE u.username='patient.binh';

INSERT INTO patients (
    user_id, patient_code, full_name, date_of_birth, gender_code, phone, email,
    address, emergency_contact_name, emergency_contact_phone, blood_type, allergies, status
)
SELECT u.id, 'PT0003', 'Lê Ngọc Chi', '2005-01-09', 'FEMALE', '0900000013',
       'patient.chi@example.test', 'Hà Nội', 'Lê Văn C', '0900000093',
       'B+', 'Không ghi nhận dị ứng', 'ACTIVE'
FROM users u WHERE u.username='patient.chi';

INSERT INTO doctors (user_id, doctor_code, specialty, license_no, years_experience, bio, status)
SELECT u.id, 'BS0001', 'Nội tổng quát', 'DEMO-LICENSE-001', 8,
       'Hồ sơ bác sĩ giả lập phục vụ kiểm thử.', 'ACTIVE'
FROM users u WHERE u.username='doctor.lan';

INSERT INTO doctors (user_id, doctor_code, specialty, license_no, years_experience, bio, status)
SELECT u.id, 'BS0002', 'Tai Mũi Họng', 'DEMO-LICENSE-002', 6,
       'Hồ sơ bác sĩ giả lập phục vụ kiểm thử.', 'ACTIVE'
FROM users u WHERE u.username='doctor.huy';

INSERT INTO services (code, name, description, duration_minutes, price, status) VALUES
('SVC-GEN', 'Khám nội tổng quát', 'Khám và tư vấn nội tổng quát', 30, 150000.00, 'ACTIVE'),
('SVC-ENT', 'Khám Tai Mũi Họng', 'Khám và tư vấn Tai Mũi Họng', 30, 180000.00, 'ACTIVE'),
('SVC-FOLLOW', 'Tái khám', 'Tái khám theo lịch hẹn', 20, 100000.00, 'ACTIVE');

INSERT INTO schedules (doctor_id, schedule_date, start_time, end_time, status, note)
SELECT d.id, '2026-09-24', '08:00:00', '08:30:00', 'OPEN', 'Slot demo'
FROM doctors d WHERE d.doctor_code='BS0001';
INSERT INTO schedules (doctor_id, schedule_date, start_time, end_time, status, note)
SELECT d.id, '2026-09-24', '08:30:00', '09:00:00', 'OPEN', 'Slot demo'
FROM doctors d WHERE d.doctor_code='BS0001';
INSERT INTO schedules (doctor_id, schedule_date, start_time, end_time, status, note)
SELECT d.id, '2026-09-24', '09:00:00', '09:30:00', 'OPEN', 'Slot demo'
FROM doctors d WHERE d.doctor_code='BS0001';
INSERT INTO schedules (doctor_id, schedule_date, start_time, end_time, status, note)
SELECT d.id, '2026-09-24', '08:00:00', '08:30:00', 'OPEN', 'Slot demo'
FROM doctors d WHERE d.doctor_code='BS0002';
INSERT INTO schedules (doctor_id, schedule_date, start_time, end_time, status, note)
SELECT d.id, '2026-09-24', '08:30:00', '09:00:00', 'OPEN', 'Slot demo'
FROM doctors d WHERE d.doctor_code='BS0002';

INSERT INTO medical_records (
    patient_id, record_code, blood_type, chronic_conditions,
    allergy_notes, medical_history, current_medications, status
)
SELECT id, 'MR0001', 'O+', 'Không ghi nhận bệnh mạn tính trong dữ liệu demo.',
       'Không ghi nhận dị ứng.', 'Tiền sử giả lập: không có thông tin đặc biệt.',
       'Không dùng thuốc thường xuyên.', 'ACTIVE'
FROM patients WHERE patient_code='PT0001';

INSERT INTO medical_records (
    patient_id, record_code, blood_type, chronic_conditions,
    allergy_notes, medical_history, current_medications, status
)
SELECT id, 'MR0002', 'A+', 'Không ghi nhận bệnh mạn tính trong dữ liệu demo.',
       'Dữ liệu demo: dị ứng penicillin.', 'Tiền sử giả lập: viêm họng tái diễn.',
       'Không dùng thuốc thường xuyên.', 'ACTIVE'
FROM patients WHERE patient_code='PT0002';

INSERT INTO medical_records (
    patient_id, record_code, blood_type, chronic_conditions,
    allergy_notes, medical_history, current_medications, status
)
SELECT id, 'MR0003', 'B+', 'Không ghi nhận bệnh mạn tính trong dữ liệu demo.',
       'Không ghi nhận dị ứng.', 'Tiền sử giả lập: không có thông tin đặc biệt.',
       'Không dùng thuốc thường xuyên.', 'ACTIVE'
FROM patients WHERE patient_code='PT0003';

-- Hai lịch hẹn ở hai slot khác nhau để tránh vi phạm UNIQUE(schedule_id).
INSERT INTO appointments (
    appointment_code, patient_id, doctor_id, schedule_id, service_id,
    appointment_date, start_time, end_time, reason, status
)
SELECT 'APT0001', p.id, d.id, s.id, sv.id,
       s.schedule_date, s.start_time, s.end_time,
       'Khám định kỳ', 'CONFIRMED'
FROM patients p
JOIN doctors d ON d.doctor_code='BS0001'
JOIN schedules s ON s.doctor_id=d.id
JOIN services sv ON sv.code='SVC-GEN'
WHERE p.patient_code='PT0001'
  AND s.schedule_date='2026-09-24'
  AND s.start_time='08:00:00';

INSERT INTO appointments (
    appointment_code, patient_id, doctor_id, schedule_id, service_id,
    appointment_date, start_time, end_time, reason, status
)
SELECT 'APT0002', p.id, d.id, s.id, sv.id,
       s.schedule_date, s.start_time, s.end_time,
       'Khám triệu chứng', 'BOOKED'
FROM patients p
JOIN doctors d ON d.doctor_code='BS0002'
JOIN schedules s ON s.doctor_id=d.id
JOIN services sv ON sv.code='SVC-ENT'
WHERE p.patient_code='PT0002'
  AND s.schedule_date='2026-09-24'
  AND s.start_time='08:00:00';

INSERT INTO encounters (
    medical_record_id, appointment_id, doctor_id, encounter_at,
    chief_complaint, diagnosis, clinical_notes, treatment_plan, follow_up_note, status
)
SELECT mr.id, a.id, d.id, '2026-09-24 08:05:00',
       'Khám định kỳ', 'Theo dõi sức khỏe tổng quát.',
       'Dữ liệu ghi chú khám giả lập.', 'Nghỉ ngơi, uống đủ nước; theo dõi triệu chứng.',
       'Tái khám nếu có triệu chứng bất thường.', 'CLOSED'
FROM medical_records mr
JOIN patients p ON p.id=mr.patient_id
JOIN appointments a ON a.appointment_code='APT0001' AND a.patient_id=p.id
JOIN doctors d ON d.doctor_code='BS0001';

INSERT INTO prescriptions (encounter_id, prescription_code, notes, status)
SELECT e.id, 'RX0001', 'Đơn thuốc demo.', 'ACTIVE'
FROM encounters e
JOIN appointments a ON a.id=e.appointment_id
WHERE a.appointment_code='APT0001';

INSERT INTO prescription_items (
    prescription_id, medicine_name, dosage, frequency, duration_days, quantity, instructions
)
SELECT p.id, 'Vitamin C (demo)', '500 mg', '1 lần/ngày', 5, 5,
       'Dùng sau ăn. Chỉ là dữ liệu mô phỏng.'
FROM prescriptions p WHERE p.prescription_code='RX0001';

INSERT INTO invoices (
    invoice_code, patient_id, appointment_id, subtotal, discount_amount,
    total_amount, status, issued_at, paid_at
)
SELECT 'INV0001', a.patient_id, a.id, 150000.00, 0.00,
       150000.00, 'PAID', '2026-09-24 08:40:00', '2026-09-24 08:45:00'
FROM appointments a WHERE a.appointment_code='APT0001';

INSERT INTO invoice_items (
    invoice_id, service_id, description, quantity, unit_price, line_total
)
SELECT i.id, s.id, s.name, 1.00, s.price, s.price
FROM invoices i
JOIN appointments a ON a.id=i.appointment_id
JOIN services s ON s.id=a.service_id
WHERE i.invoice_code='INV0001';

-- Audit demo: bác sĩ xem bệnh án của bệnh nhân mình điều trị.
INSERT INTO audit_logs (
    actor_user_id, action_code, entity_type, entity_id, ip_address, user_agent, metadata_json
)
SELECT u.id, 'MEDICAL_RECORD_VIEW', 'medical_records', mr.id,
       '127.0.0.1', 'Demo Browser',
       JSON_OBJECT('reason', 'doctor_treatment_access', 'demo', TRUE)
FROM users u
JOIN doctors d ON d.user_id=u.id AND d.doctor_code='BS0001'
JOIN encounters e ON e.doctor_id=d.id
JOIN medical_records mr ON mr.id=e.medical_record_id
WHERE u.username='doctor.lan'
LIMIT 1;

COMMIT;
