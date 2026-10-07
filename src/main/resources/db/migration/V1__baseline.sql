-- =====================================================================
-- MedBook - V1: lược đồ nền (MySQL 8, utf8mb4)
--
-- Trạng thái lược đồ ở bản nghiệm thu Buổi 05-06, khớp với các entity JPA và với các kịch bản đã nhập tay trên
-- CSDL nghiệm thu (scripts/auth-refresh-schema.mysql.sql, scripts/buoi06-v2-invariants.mysql.sql).
--
-- CSDL MỚI: Flyway chạy tệp này rồi tới các bản sau.
-- CSDL ĐÃ CÓ (nghiệm thu, hoặc volume Docker cũ dựng bằng ddl-auto=update): KHÔNG chạy tệp này. Hồ sơ docker và
-- prod bật baseline-on-migrate với baseline-version=1, nên Flyway coi V1 là đã có và chỉ chạy từ V2.
--
-- Chỉ dùng cú pháp mà H2 ở chế độ MySQL cũng hiểu, để bộ test kiểm tra được toàn bộ migration không cần MySQL.
-- =====================================================================

-- ---------- Tài khoản và phân quyền ----------

CREATE TABLE roles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(50) NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(190) DEFAULT NULL,
  phone VARCHAR(30) DEFAULT NULL,
  status VARCHAR(20) NOT NULL,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME(6) DEFAULT NULL,
  token_version INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
  role_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (role_id, user_id),
  KEY idx_user_roles_user (user_id),
  CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auth_sessions (
  id VARCHAR(36) NOT NULL,
  user_id BIGINT NOT NULL,
  token_version INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_auth_sessions_user (user_id),
  KEY idx_auth_sessions_expiry (expires_at),
  CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
  token_hash CHAR(64) NOT NULL,
  session_id VARCHAR(36) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (token_hash),
  KEY idx_refresh_tokens_session (session_id),
  CONSTRAINT fk_refresh_tokens_session FOREIGN KEY (session_id) REFERENCES auth_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE revoked_tokens (
  jti VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  revoked_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  PRIMARY KEY (jti)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE password_reset_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_password_reset_tokens_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- Danh mục ----------

CREATE TABLE specialties (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(50) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500) DEFAULT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_specialties_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE services (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(30) NOT NULL,
  name VARCHAR(150) NOT NULL,
  description VARCHAR(500) DEFAULT NULL,
  duration_minutes INT NOT NULL,
  price DECIMAL(12,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_services_code (code),
  CONSTRAINT chk_services_price CHECK (price >= 0),
  CONSTRAINT chk_services_duration CHECK (duration_minutes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE medicines (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(50) NOT NULL,
  name VARCHAR(200) NOT NULL,
  unit VARCHAR(50) DEFAULT NULL,
  description VARCHAR(500) DEFAULT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_medicines_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- Bác sĩ và bệnh nhân ----------

CREATE TABLE doctors (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  specialty_id BIGINT DEFAULT NULL,
  full_name VARCHAR(100) NOT NULL,
  phone VARCHAR(20) DEFAULT NULL,
  license_number VARCHAR(50) DEFAULT NULL,
  bio TEXT,
  is_active BIT(1) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_doctors_user (user_id),
  KEY idx_doctors_specialty (specialty_id),
  CONSTRAINT fk_doctors_specialty FOREIGN KEY (specialty_id) REFERENCES specialties (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE patients (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT DEFAULT NULL,
  patient_code VARCHAR(30) NOT NULL,
  full_name VARCHAR(150) NOT NULL,
  date_of_birth DATE NOT NULL,
  gender_code VARCHAR(20) NOT NULL,
  phone VARCHAR(30) NOT NULL,
  email VARCHAR(190) DEFAULT NULL,
  address VARCHAR(255) DEFAULT NULL,
  emergency_contact_name VARCHAR(150) DEFAULT NULL,
  emergency_contact_phone VARCHAR(30) DEFAULT NULL,
  blood_type VARCHAR(5) DEFAULT NULL,
  allergies LONGTEXT,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_patients_code (patient_code),
  UNIQUE KEY uk_patients_user (user_id),
  CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE medical_records (
  id BIGINT NOT NULL AUTO_INCREMENT,
  patient_id BIGINT NOT NULL,
  record_code VARCHAR(40) NOT NULL,
  blood_type VARCHAR(5) DEFAULT NULL,
  allergy_notes TINYTEXT,
  chronic_conditions TINYTEXT,
  current_medications TINYTEXT,
  medical_history TINYTEXT,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_medical_records_code (record_code),
  UNIQUE KEY uk_medical_records_patient (patient_id),
  CONSTRAINT fk_medical_records_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- Lịch làm việc, giờ trống và lịch hẹn ----------

CREATE TABLE doctor_schedules (
  id BIGINT NOT NULL AUTO_INCREMENT,
  doctor_id BIGINT NOT NULL,
  work_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_doctor_schedules_doctor (doctor_id),
  CONSTRAINT fk_doctor_schedules_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
  CONSTRAINT chk_schedules_time CHECK (end_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE schedule_breaks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  doctor_schedule_id BIGINT NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  reason VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_schedule_breaks_schedule (doctor_schedule_id),
  CONSTRAINT fk_schedule_breaks_schedule FOREIGN KEY (doctor_schedule_id) REFERENCES doctor_schedules (id),
  CONSTRAINT chk_breaks_time CHECK (end_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE appointment_slots (
  id BIGINT NOT NULL AUTO_INCREMENT,
  doctor_id BIGINT NOT NULL,
  slot_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  is_available BIT(1) NOT NULL,
  status VARCHAR(20) NOT NULL,
  note VARCHAR(255) DEFAULT NULL,
  version BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_appointment_slots_booking (doctor_id, slot_date, status, is_available),
  CONSTRAINT fk_appointment_slots_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
  CONSTRAINT chk_slots_time CHECK (end_time > start_time),
  CONSTRAINT chk_slots_status CHECK (status IN ('AVAILABLE', 'BOOKED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- active_slot_id = slot_id khi lịch còn giữ chỗ, NULL khi đã hủy: UNIQUE bảo đảm mỗi slot chỉ có một lịch đang giữ.
CREATE TABLE appointments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  patient_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  slot_id BIGINT NOT NULL,
  active_slot_id BIGINT DEFAULT NULL,
  service_id BIGINT DEFAULT NULL,
  status ENUM('BOOKED','CANCELLED','COMPLETED','IN_PROGRESS') NOT NULL,
  notes TEXT,
  cancel_reason VARCHAR(500) DEFAULT NULL,
  cancelled_at DATETIME(6) DEFAULT NULL,
  reminder_24h_sent_at DATETIME(6) DEFAULT NULL,
  reminder_2h_sent_at DATETIME(6) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_appointments_active_slot (active_slot_id),
  KEY idx_appointments_doctor (doctor_id),
  KEY idx_appointments_slot (slot_id),
  CONSTRAINT fk_appointments_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
  CONSTRAINT fk_appointments_slot FOREIGN KEY (slot_id) REFERENCES appointment_slots (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- Khám bệnh ----------

CREATE TABLE encounters (
  id BIGINT NOT NULL AUTO_INCREMENT,
  medical_record_id BIGINT NOT NULL,
  appointment_id BIGINT DEFAULT NULL,
  doctor_id BIGINT NOT NULL,
  encounter_at DATETIME(6) NOT NULL,
  status VARCHAR(20) NOT NULL,
  chief_complaint TEXT,
  clinical_notes TEXT,
  diagnosis TEXT,
  treatment_plan TEXT,
  follow_up_note TEXT,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_encounters_appointment (appointment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attachments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  encounter_id BIGINT NOT NULL,
  original_file_name VARCHAR(255) NOT NULL,
  stored_file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  file_size BIGINT NOT NULL,
  uploaded_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_attachments_stored_name (stored_file_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE prescriptions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  encounter_id BIGINT NOT NULL,
  prescription_code VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL,
  notes VARCHAR(500) DEFAULT NULL,
  issued_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_prescriptions_code (prescription_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE prescription_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  prescription_id BIGINT NOT NULL,
  medicine_name VARCHAR(200) NOT NULL,
  dosage VARCHAR(100) DEFAULT NULL,
  frequency VARCHAR(100) DEFAULT NULL,
  duration_days INT DEFAULT NULL,
  quantity DECIMAL(10,2) NOT NULL,
  instructions VARCHAR(500) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_presc_items_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- Hóa đơn ----------

CREATE TABLE invoices (
  id BIGINT NOT NULL AUTO_INCREMENT,
  invoice_code VARCHAR(40) NOT NULL,
  patient_id BIGINT NOT NULL,
  appointment_id BIGINT DEFAULT NULL,
  subtotal DECIMAL(12,2) NOT NULL,
  discount_amount DECIMAL(12,2) NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  issued_at DATETIME(6) DEFAULT NULL,
  paid_at DATETIME(6) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invoices_code (invoice_code),
  UNIQUE KEY uk_invoices_appointment (appointment_id),
  CONSTRAINT chk_invoices_status CHECK (status IN ('UNPAID', 'PAID', 'VOID')),
  CONSTRAINT chk_invoices_amount CHECK (subtotal >= 0 AND discount_amount >= 0 AND total_amount >= 0
      AND discount_amount <= subtotal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE invoice_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  invoice_id BIGINT NOT NULL,
  service_id BIGINT DEFAULT NULL,
  description VARCHAR(255) NOT NULL,
  quantity DECIMAL(10,2) NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  line_total DECIMAL(12,2) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_invoice_items CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- Nhật ký hệ thống ----------

CREATE TABLE audit_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT DEFAULT NULL,
  action_code VARCHAR(50) NOT NULL,
  entity_type VARCHAR(80) NOT NULL,
  entity_id BIGINT DEFAULT NULL,
  ip_address VARCHAR(45) DEFAULT NULL,
  user_agent VARCHAR(500) DEFAULT NULL,
  metadata_json JSON DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_logs_actor (actor_user_id),
  CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
