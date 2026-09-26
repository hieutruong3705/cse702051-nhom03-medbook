USE medbook_db;

-- ============================================================
-- DEV 4 - MEDICAL ENCOUNTER / PRESCRIPTION / INVOICE / ATTACHMENT
-- Migration
-- ============================================================

-- ------------------------------------------------------------
-- 1. Attachments
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attachments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    encounter_id BIGINT UNSIGNED NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL UNIQUE,
    file_path VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT UNSIGNED NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_attachments_encounter
        FOREIGN KEY (encounter_id)
        REFERENCES encounters(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT chk_attachments_file_size
        CHECK (file_size > 0),

    INDEX idx_attachments_encounter (encounter_id)
);

-- ------------------------------------------------------------
-- 2. Prescription indexes
-- ------------------------------------------------------------

-- prescriptions.encounter_id đã có index trong database.
-- Không tạo lại để tránh duplicate index.

CREATE INDEX idx_prescriptions_status
    ON prescriptions(status);

-- prescription_items.prescription_id đã có index trong database.
-- Chỉ bổ sung index tìm kiếm theo tên thuốc.

CREATE INDEX idx_prescription_items_medicine_name
    ON prescription_items(medicine_name);

-- ------------------------------------------------------------
-- 3. Invoice indexes
-- ------------------------------------------------------------

CREATE INDEX idx_invoices_patient_date
    ON invoices(patient_id, created_at);

CREATE INDEX idx_invoices_status
    ON invoices(status);

-- invoice_items.invoice_id và invoice_items.service_id
-- đã có index trong database hiện tại.
-- Không tạo lại để tránh duplicate index.