-- =====================================================================
-- MedBook - dữ liệu mẫu để trình diễn mọi chức năng (MySQL 8). Nạp SAU schema.sql, vào cơ sở dữ liệu còn trống:
--     mysql -u <người dùng> -p <tên CSDL> < schema.sql
--     mysql -u <người dùng> -p <tên CSDL> < seed.sql
--
-- Toàn bộ là dữ liệu hư cấu phục vụ học tập: không có họ tên, số điện thoại, địa chỉ hay bệnh án của người thật.
--
-- Sáu tài khoản mẫu, đủ bốn vai trò, dùng chung mật khẩu trình diễn ghi trong README.md (mục "Tài khoản demo").
-- Trong tệp chỉ có mật khẩu ở dạng đã băm BCrypt (cost 12).
--     admin1 (Quản trị viên), doctor1, doctor2 (Bác sĩ), receptionist1 (Lễ tân), patient1, patient2 (Bệnh nhân)
--
-- Ngày trong tệp tính theo "hôm nay" của máy chủ CSDL lúc nạp (CURDATE()), nên nạp lúc nào cũng có:
--   - ca làm việc và khung giờ trống của hai bác sĩ trong 7 ngày kể từ ngày mai;
--   - lịch hẹn ở cả bốn trạng thái: Đã đặt (ngày mai, ngày kia), Đang khám (hôm nay), Đã khám, Đã hủy (các ngày trước);
--   - lần khám, đơn thuốc, hóa đơn đã thu, chưa thu và đã hủy.
-- Giờ khám trong tệp (08:00, 14:00...) là giờ ứng dụng hiển thị khi máy chủ ứng dụng và máy chủ CSDL cùng múi giờ, như bộ
-- Docker Compose của dự án (cả hai chạy giờ UTC).
-- Phần A giống hệt dữ liệu ứng dụng tự nạp khi MEDBOOK_SEED_DEMO=true (src/main/resources/db/seed/demo-seed.sql).
-- =====================================================================

SET NAMES utf8mb4;
SET @today = CURDATE();

-- ---------------------------------------------------------------------
-- A. Tài khoản, danh mục, ca làm việc và khung giờ trống
-- ---------------------------------------------------------------------

INSERT INTO roles (id, code, name, description, created_at) VALUES
(1, 'PATIENT', 'Bệnh nhân', 'Người bệnh đăng ký khám', NOW()),
(2, 'DOCTOR', 'Bác sĩ', 'Bác sĩ khám chữa bệnh', NOW()),
(3, 'ADMIN', 'Quản trị viên', 'Quản trị hệ thống', NOW()),
(4, 'RECEPTIONIST', 'Lễ tân', 'Nhân viên tiếp đón tại quầy', NOW());

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
(5, 'patient2', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Trần Gia Bình', 'patient2@medbook.local', '0945678901', 'ACTIVE', 0, 0, NOW(), NOW()),
(6, 'receptionist1', '$2a$12$7MBzRjriXr4ThmBO5b01rOlQFYbUFi2u00ZcWn1ntMiN59J.TnJai', 'Phạm Thu Hà', 'receptionist1@medbook.local', '0956789012', 'ACTIVE', 0, 0, NOW(), NOW());

INSERT INTO user_roles (user_id, role_id, created_at) VALUES
(1, 3, NOW()),
(2, 2, NOW()),
(3, 2, NOW()),
(4, 1, NOW()),
(5, 1, NOW()),
(6, 4, NOW());

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
-- bác sĩ 1 sáng 08:00-10:00 và chiều 14:00-15:30, bác sĩ 2 sáng 08:00-09:30, mỗi slot 30 phút; bác sĩ 2 thêm ca
-- chiều 15:00-16:30 với slot 45 phút, để dịch vụ dài nhất (DV02, 45 phút) cũng có khung giờ đủ dài.
INSERT INTO doctor_schedules (doctor_id, work_date, start_time, end_time, slot_minutes, created_at, updated_at)
SELECT s.doctor_id, TIMESTAMPADD(DAY, d.n, @today), s.start_time, s.end_time, s.slot_minutes, NOW(), NOW()
FROM (SELECT 1 AS doctor_id, '08:00:00' AS start_time, '10:00:00' AS end_time, 30 AS slot_minutes
      UNION ALL SELECT 1, '14:00:00', '15:30:00', 30
      UNION ALL SELECT 2, '08:00:00', '09:30:00', 30
      UNION ALL SELECT 2, '15:00:00', '16:30:00', 45) s
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) d;

INSERT INTO appointment_slots (doctor_id, slot_date, start_time, end_time, is_available, status, version, created_at, updated_at)
SELECT t.doctor_id, TIMESTAMPADD(DAY, d.n, @today), t.start_time, t.end_time, TRUE, 'AVAILABLE', 0, NOW(), NOW()
FROM (SELECT 1 AS doctor_id, '08:00:00' AS start_time, '08:30:00' AS end_time
      UNION ALL SELECT 1, '08:30:00', '09:00:00'
      UNION ALL SELECT 1, '09:00:00', '09:30:00'
      UNION ALL SELECT 1, '09:30:00', '10:00:00'
      UNION ALL SELECT 1, '14:00:00', '14:30:00'
      UNION ALL SELECT 1, '14:30:00', '15:00:00'
      UNION ALL SELECT 1, '15:00:00', '15:30:00'
      UNION ALL SELECT 2, '08:00:00', '08:30:00'
      UNION ALL SELECT 2, '08:30:00', '09:00:00'
      UNION ALL SELECT 2, '09:00:00', '09:30:00'
      UNION ALL SELECT 2, '15:00:00', '15:45:00'
      UNION ALL SELECT 2, '15:45:00', '16:30:00') t
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) d;

-- ---------------------------------------------------------------------
-- B. Hoạt động mẫu: bệnh nhân vãng lai, bệnh án, lịch hẹn, lần khám, đơn thuốc, hóa đơn
-- ---------------------------------------------------------------------

-- Bệnh nhân vãng lai do lễ tân lập hồ sơ tại quầy: không có tài khoản đăng nhập (user_id NULL).
INSERT INTO patients (id, user_id, patient_code, full_name, date_of_birth, gender_code, phone, email, address, status, created_at, updated_at) VALUES
(3, NULL, 'BN000003', 'Lê Thị Cúc', '1978-09-20', 'FEMALE', '0978901234', NULL, 'Hà Nội', 'ACTIVE', NOW(), NOW());

-- Mỗi bệnh nhân một bệnh án.
INSERT INTO medical_records (id, patient_id, record_code, blood_type, allergy_notes, chronic_conditions, current_medications, medical_history, status, created_at, updated_at) VALUES
(1, 1, 'MR000001', 'O', 'Không', NULL, NULL, 'Dữ liệu mẫu, không phải bệnh sử thật', 'ACTIVE', NOW(), NOW()),
(2, 2, 'MR000002', 'A', 'Phấn hoa', NULL, NULL, 'Dữ liệu mẫu, không phải bệnh sử thật', 'ACTIVE', NOW(), NOW()),
(3, 3, 'MR000003', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NOW(), NOW());

-- Các ngày dùng bên dưới.
SET @d_4 = DATE_SUB(@today, INTERVAL 4 DAY);
SET @d_3 = DATE_SUB(@today, INTERVAL 3 DAY);
SET @d_2 = DATE_SUB(@today, INTERVAL 2 DAY);
SET @d_1 = DATE_SUB(@today, INTERVAL 1 DAY);
SET @d1 = DATE_ADD(@today, INTERVAL 1 DAY);
SET @d2 = DATE_ADD(@today, INTERVAL 2 DAY);

-- ===== 1. Đã khám, có đơn thuốc, hóa đơn ĐÃ THU: patient1 khám bác sĩ Lan hai ngày trước =====
INSERT INTO appointment_slots (doctor_id, slot_date, start_time, end_time, is_available, status, version, created_at, updated_at)
VALUES (1, @d_2, '09:00:00', '09:30:00', FALSE, 'BOOKED', 0, NOW(), NOW());
SET @slot = LAST_INSERT_ID();
INSERT INTO appointments (patient_id, doctor_id, slot_id, active_slot_id, service_id, status, notes, created_at, updated_at)
VALUES (1, 1, @slot, @slot, 1, 'COMPLETED', 'Đau đầu, mệt mỏi (dữ liệu mẫu)', TIMESTAMP(@d_4, '10:00:00'), TIMESTAMP(@d_2, '09:30:00'));
SET @appointment = LAST_INSERT_ID();
INSERT INTO encounters (medical_record_id, appointment_id, doctor_id, encounter_at, status, chief_complaint, clinical_notes, diagnosis, treatment_plan, follow_up_note, created_at, updated_at)
VALUES (1, @appointment, 1, TIMESTAMP(@d_2, '09:00:00'), 'COMPLETED', 'Đau đầu, mệt mỏi (dữ liệu mẫu)', 'Dữ liệu mẫu, không phải ghi chú lâm sàng thật',
        'Dữ liệu mẫu, không phải chẩn đoán thật', 'Nghỉ ngơi, uống đủ nước (dữ liệu mẫu)', 'Tái khám sau 7 ngày nếu còn triệu chứng',
        TIMESTAMP(@d_2, '09:00:00'), TIMESTAMP(@d_2, '09:30:00'));
SET @encounter = LAST_INSERT_ID();
INSERT INTO prescriptions (encounter_id, prescription_code, status, notes, issued_at, created_at, updated_at)
VALUES (@encounter, CONCAT('RX-', DATE_FORMAT(@d_2, '%Y%m%d'), '-MAU001'), 'ACTIVE', 'Uống sau ăn', TIMESTAMP(@d_2, '09:25:00'), TIMESTAMP(@d_2, '09:25:00'), TIMESTAMP(@d_2, '09:25:00'));
SET @prescription = LAST_INSERT_ID();
INSERT INTO prescription_items (prescription_id, medicine_id, medicine_name, dosage, frequency, duration_days, quantity, instructions, created_at) VALUES
(@prescription, 1, 'Paracetamol 500mg', '1 viên', '2 lần mỗi ngày', 3, 6.00, 'Uống khi đau, cách nhau ít nhất 6 giờ', TIMESTAMP(@d_2, '09:25:00')),
(@prescription, 3, 'Vitamin C 500mg', '1 viên', '1 lần mỗi ngày', 7, 7.00, 'Uống buổi sáng', TIMESTAMP(@d_2, '09:25:00'));
INSERT INTO invoices (invoice_code, patient_id, appointment_id, subtotal, discount_amount, total_amount, status, issued_at, paid_at, created_at, updated_at)
VALUES (CONCAT('INV-', DATE_FORMAT(@d_2, '%Y%m%d'), '-MAU001'), 1, @appointment, 200000.00, 0.00, 200000.00, 'PAID',
        TIMESTAMP(@d_2, '09:30:00'), TIMESTAMP(@d_2, '09:40:00'), TIMESTAMP(@d_2, '09:30:00'), TIMESTAMP(@d_2, '09:40:00'));
SET @invoice = LAST_INSERT_ID();
INSERT INTO invoice_items (invoice_id, service_id, description, quantity, unit_price, line_total, created_at) VALUES
(@invoice, 1, 'Khám tổng quát', 1.00, 200000.00, 200000.00, TIMESTAMP(@d_2, '09:30:00'));

-- ===== 2. Đã khám, hóa đơn CHƯA THU (để lễ tân hoặc quản trị viên ghi nhận đã thu): patient2 khám bác sĩ Huy hôm qua =====
INSERT INTO appointment_slots (doctor_id, slot_date, start_time, end_time, is_available, status, version, created_at, updated_at)
VALUES (2, @d_1, '15:00:00', '15:45:00', FALSE, 'BOOKED', 0, NOW(), NOW());
SET @slot = LAST_INSERT_ID();
INSERT INTO appointments (patient_id, doctor_id, slot_id, active_slot_id, service_id, status, notes, created_at, updated_at)
VALUES (2, 2, @slot, @slot, 2, 'COMPLETED', 'Khám chuyên khoa theo hẹn (dữ liệu mẫu)', TIMESTAMP(@d_3, '14:00:00'), TIMESTAMP(@d_1, '15:45:00'));
SET @appointment = LAST_INSERT_ID();
INSERT INTO encounters (medical_record_id, appointment_id, doctor_id, encounter_at, status, chief_complaint, diagnosis, created_at, updated_at)
VALUES (2, @appointment, 2, TIMESTAMP(@d_1, '15:00:00'), 'COMPLETED', 'Khám chuyên khoa theo hẹn (dữ liệu mẫu)', 'Dữ liệu mẫu, không phải chẩn đoán thật',
        TIMESTAMP(@d_1, '15:00:00'), TIMESTAMP(@d_1, '15:45:00'));
INSERT INTO invoices (invoice_code, patient_id, appointment_id, subtotal, discount_amount, total_amount, status, issued_at, paid_at, created_at, updated_at)
VALUES (CONCAT('INV-', DATE_FORMAT(@d_1, '%Y%m%d'), '-MAU002'), 2, @appointment, 450000.00, 20000.00, 430000.00, 'UNPAID',
        TIMESTAMP(@d_1, '15:45:00'), NULL, TIMESTAMP(@d_1, '15:45:00'), TIMESTAMP(@d_1, '15:45:00'));
SET @invoice = LAST_INSERT_ID();
INSERT INTO invoice_items (invoice_id, service_id, description, quantity, unit_price, line_total, created_at) VALUES
(@invoice, 2, 'Khám chuyên khoa', 1.00, 300000.00, 300000.00, TIMESTAMP(@d_1, '15:45:00')),
(@invoice, 3, 'Xét nghiệm máu', 1.00, 150000.00, 150000.00, TIMESTAMP(@d_1, '15:45:00'));

-- ===== 3. Đã khám, hóa đơn ĐÃ HỦY (lập nhầm): patient2 khám bác sĩ Lan bốn ngày trước =====
INSERT INTO appointment_slots (doctor_id, slot_date, start_time, end_time, is_available, status, version, created_at, updated_at)
VALUES (1, @d_4, '14:00:00', '14:30:00', FALSE, 'BOOKED', 0, NOW(), NOW());
SET @slot = LAST_INSERT_ID();
INSERT INTO appointments (patient_id, doctor_id, slot_id, active_slot_id, service_id, status, notes, created_at, updated_at)
VALUES (2, 1, @slot, @slot, 4, 'COMPLETED', 'Siêu âm theo chỉ định (dữ liệu mẫu)', TIMESTAMP(DATE_SUB(@d_4, INTERVAL 2 DAY), '08:15:00'), TIMESTAMP(@d_4, '14:30:00'));
SET @appointment = LAST_INSERT_ID();
INSERT INTO encounters (medical_record_id, appointment_id, doctor_id, encounter_at, status, chief_complaint, diagnosis, created_at, updated_at)
VALUES (2, @appointment, 1, TIMESTAMP(@d_4, '14:00:00'), 'COMPLETED', 'Siêu âm theo chỉ định (dữ liệu mẫu)', 'Dữ liệu mẫu, không phải chẩn đoán thật',
        TIMESTAMP(@d_4, '14:00:00'), TIMESTAMP(@d_4, '14:30:00'));
INSERT INTO invoices (invoice_code, patient_id, appointment_id, subtotal, discount_amount, total_amount, status, issued_at, paid_at, voided_at, void_reason, created_at, updated_at)
VALUES (CONCAT('INV-', DATE_FORMAT(@d_4, '%Y%m%d'), '-MAU003'), 2, @appointment, 250000.00, 0.00, 250000.00, 'VOID',
        TIMESTAMP(@d_4, '14:30:00'), NULL, TIMESTAMP(@d_4, '16:00:00'), 'Lập nhầm dịch vụ', TIMESTAMP(@d_4, '14:30:00'), TIMESTAMP(@d_4, '16:00:00'));
SET @invoice = LAST_INSERT_ID();
INSERT INTO invoice_items (invoice_id, service_id, description, quantity, unit_price, line_total, created_at) VALUES
(@invoice, 4, 'Siêu âm', 1.00, 250000.00, 250000.00, TIMESTAMP(@d_4, '14:30:00'));

-- ===== 4. ĐÃ HỦY: patient1 hủy lịch với bác sĩ Huy ba ngày trước; khung giờ được nhả lại =====
INSERT INTO appointment_slots (doctor_id, slot_date, start_time, end_time, is_available, status, version, created_at, updated_at)
VALUES (2, @d_3, '08:00:00', '08:30:00', TRUE, 'AVAILABLE', 0, NOW(), NOW());
SET @slot = LAST_INSERT_ID();
INSERT INTO appointments (patient_id, doctor_id, slot_id, active_slot_id, service_id, status, notes, cancel_reason, cancelled_at, created_at, updated_at)
VALUES (1, 2, @slot, NULL, 1, 'CANCELLED', 'Khám định kỳ (dữ liệu mẫu)', 'Bệnh nhân báo bận', TIMESTAMP(@d_4, '18:00:00'),
        TIMESTAMP(DATE_SUB(@d_4, INTERVAL 1 DAY), '09:00:00'), TIMESTAMP(@d_4, '18:00:00'));

-- ===== 5. ĐANG KHÁM hôm nay: bệnh nhân vãng lai, lễ tân đặt hộ, bác sĩ Lan đã bắt đầu khám =====
INSERT INTO appointment_slots (doctor_id, slot_date, start_time, end_time, is_available, status, version, created_at, updated_at)
VALUES (1, @today, '08:00:00', '08:30:00', FALSE, 'BOOKED', 0, NOW(), NOW());
SET @slot = LAST_INSERT_ID();
INSERT INTO appointments (patient_id, doctor_id, slot_id, active_slot_id, service_id, status, notes, created_at, updated_at)
VALUES (3, 1, @slot, @slot, 1, 'IN_PROGRESS', 'Đặt tại quầy (dữ liệu mẫu)', TIMESTAMP(@d_1, '16:30:00'), TIMESTAMP(@today, '08:00:00'));
SET @appointment = LAST_INSERT_ID();
INSERT INTO encounters (medical_record_id, appointment_id, doctor_id, encounter_at, status, chief_complaint, created_at, updated_at)
VALUES (3, @appointment, 1, TIMESTAMP(@today, '08:00:00'), 'OPEN', 'Ho, sổ mũi ba ngày (dữ liệu mẫu)', TIMESTAMP(@today, '08:00:00'), TIMESTAMP(@today, '08:00:00'));

-- ===== 6. ĐÃ ĐẶT, chưa khám: dùng hai khung giờ trống của phần A =====
-- patient2 tự đặt bác sĩ Lan 08:00 ngày mai.
SET @slot = (SELECT id FROM appointment_slots WHERE doctor_id = 1 AND slot_date = @d1 AND start_time = '08:00:00');
UPDATE appointment_slots SET is_available = FALSE, status = 'BOOKED', updated_at = NOW() WHERE id = @slot;
INSERT INTO appointments (patient_id, doctor_id, slot_id, active_slot_id, service_id, status, notes, created_at, updated_at)
VALUES (2, 1, @slot, @slot, 1, 'BOOKED', 'Tái khám (dữ liệu mẫu)', NOW(), NOW());
-- Lễ tân đặt hộ bệnh nhân vãng lai với bác sĩ Huy 08:30 ngày kia.
SET @slot = (SELECT id FROM appointment_slots WHERE doctor_id = 2 AND slot_date = @d2 AND start_time = '08:30:00');
UPDATE appointment_slots SET is_available = FALSE, status = 'BOOKED', updated_at = NOW() WHERE id = @slot;
INSERT INTO appointments (patient_id, doctor_id, slot_id, active_slot_id, service_id, status, notes, created_at, updated_at)
VALUES (3, 2, @slot, @slot, 1, 'BOOKED', 'Đặt tại quầy (dữ liệu mẫu)', NOW(), NOW());
