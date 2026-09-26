USE medbook_db;

-- ============================================================
-- DEV 4 - SEED DATA
-- Encounter / Prescription / Prescription Item
-- Invoice / Invoice Item
-- ============================================================

-- ============================================================
-- 1. Seed Encounter
-- ============================================================

INSERT INTO encounters (
    medical_record_id,
    appointment_id,
    doctor_id,
    encounter_at,
    chief_complaint,
    diagnosis,
    clinical_notes,
    treatment_plan,
    follow_up_note,
    status
)
SELECT
    1,
    1,
    1,
    '2026-09-24 15:30:00',
    'Đau đầu và mệt mỏi',
    'Theo dõi đau đầu do căng thẳng',
    'Dữ liệu mẫu Dev 4.',
    'Nghỉ ngơi, uống đủ nước và dùng thuốc theo đơn.',
    'Tái khám nếu triệu chứng kéo dài.',
    'COMPLETED'
FROM DUAL
WHERE EXISTS (
    SELECT 1
    FROM medical_records
    WHERE id = 1
)
AND EXISTS (
    SELECT 1
    FROM appointments
    WHERE id = 1
)
AND EXISTS (
    SELECT 1
    FROM doctors
    WHERE id = 1
)
AND NOT EXISTS (
    SELECT 1
    FROM encounters
    WHERE appointment_id = 1
);


-- ============================================================
-- 2. Seed Prescription
-- ============================================================

INSERT INTO prescriptions (
    encounter_id,
    prescription_code,
    issued_at,
    notes,
    status
)
SELECT
    e.id,
    'RX-DEV4-DEMO',
    '2026-09-24 15:35:00',
    'Đơn thuốc mẫu Dev 4.',
    'ACTIVE'
FROM encounters e
WHERE e.appointment_id = 1
AND NOT EXISTS (
    SELECT 1
    FROM prescriptions
    WHERE prescription_code = 'RX-DEV4-DEMO'
);


-- ============================================================
-- 3. Seed Prescription Item
-- ============================================================

INSERT INTO prescription_items (
    prescription_id,
    medicine_name,
    dosage,
    frequency,
    duration_days,
    quantity,
    instructions
)
SELECT
    p.id,
    'Paracetamol 500mg',
    '500 mg',
    '2 lần/ngày',
    5,
    10.00,
    'Uống sau ăn khi cần.'
FROM prescriptions p
WHERE p.prescription_code = 'RX-DEV4-DEMO'
AND NOT EXISTS (
    SELECT 1
    FROM prescription_items
    WHERE prescription_id = p.id
);


-- ============================================================
-- 4. Seed Invoice
-- ============================================================
-- Không tạo invoice nếu appointment đã có hóa đơn.
-- Điều này tránh lỗi duplicate appointment_id.

INSERT INTO invoices (
    invoice_code,
    patient_id,
    appointment_id,
    subtotal,
    discount_amount,
    total_amount,
    status,
    issued_at,
    paid_at
)
SELECT
    'INV-DEV4-DEMO',
    a.patient_id,
    a.id,
    s.price,
    0.00,
    s.price,
    'UNPAID',
    '2026-09-24 15:40:00',
    NULL
FROM appointments a
JOIN services s
    ON s.id = (
        SELECT MIN(id)
        FROM services
    )
WHERE a.id = 1
AND NOT EXISTS (
    SELECT 1
    FROM invoices
    WHERE invoice_code = 'INV-DEV4-DEMO'
)
AND NOT EXISTS (
    SELECT 1
    FROM invoices
    WHERE appointment_id = a.id
);


-- ============================================================
-- 5. Seed Invoice Item
-- ============================================================
-- Chỉ tạo item cho invoice DEV4 nếu invoice đó thực sự được tạo.

INSERT INTO invoice_items (
    invoice_id,
    service_id,
    description,
    quantity,
    unit_price,
    line_total
)
SELECT
    i.id,
    s.id,
    s.name,
    1.00,
    s.price,
    s.price
FROM invoices i
JOIN services s
    ON s.id = (
        SELECT MIN(id)
        FROM services
    )
WHERE i.invoice_code = 'INV-DEV4-DEMO'
AND NOT EXISTS (
    SELECT 1
    FROM invoice_items
    WHERE invoice_id = i.id
);


-- ============================================================
-- NOTE
-- ============================================================
-- Attachment không seed trực tiếp bằng SQL vì file thật
-- phải được upload thông qua AttachmentService.
--
-- Attachment API:
-- POST /api/encounters/{id}/attachments
--
-- Body:
-- multipart/form-data
-- key = file
--
-- AttachmentService kiểm tra:
-- - PDF/JPG/JPEG/PNG
-- - file signature
-- - tối đa 10 MB
-- - server-generated stored file name