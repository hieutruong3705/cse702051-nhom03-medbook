package com.phenikaa.cse702051.medbook.service;

/**
 * Mã hành động và loại đối tượng dùng chung cho audit log. Mọi module dùng các hằng
 * số này (không tự đặt chuỗi) để Admin lọc được nhất quán.
 * {@code entity_type} dùng tên bảng.
 */
public final class AuditActions {

    private AuditActions() {
    }

    // --- Tài khoản / phiên ---
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String LOGIN_RATE_LIMITED = "LOGIN_RATE_LIMITED";
    public static final String ACCOUNT_STATUS_CHANGED = "ACCOUNT_STATUS_CHANGED";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    // --- Đọc dữ liệu y tế ---
    public static final String MEDICAL_RECORD_VIEW = "MEDICAL_RECORD_VIEW";
    public static final String PATIENT_VIEW = "PATIENT_VIEW";
    public static final String ENCOUNTER_VIEW = "ENCOUNTER_VIEW";
    public static final String PRESCRIPTION_VIEW = "PRESCRIPTION_VIEW";
    public static final String ATTACHMENT_DOWNLOAD = "ATTACHMENT_DOWNLOAD";
    public static final String ATTACHMENT_LIST = "ATTACHMENT_LIST";

    // --- Thay đổi dữ liệu nhạy cảm ---
    public static final String ATTACHMENT_UPLOAD = "ATTACHMENT_UPLOAD";
    public static final String ATTACHMENT_DELETE = "ATTACHMENT_DELETE";
    public static final String ENCOUNTER_CREATE = "ENCOUNTER_CREATE";
    public static final String ENCOUNTER_UPDATE = "ENCOUNTER_UPDATE";
    public static final String ENCOUNTER_COMPLETE = "ENCOUNTER_COMPLETE";
    public static final String DATA_DELETE = "DATA_DELETE";
    public static final String PRESCRIPTION_CREATE = "PRESCRIPTION_CREATE";
    public static final String PRESCRIPTION_UPDATE = "PRESCRIPTION_UPDATE";

    // --- Lễ tân làm hộ bệnh nhân tại quầy (người thực hiện là lễ tân, không phải bệnh nhân) ---
    public static final String PATIENT_CREATE = "PATIENT_CREATE";
    public static final String APPOINTMENT_BOOK_ON_BEHALF = "APPOINTMENT_BOOK_ON_BEHALF";
    public static final String APPOINTMENT_CANCEL_ON_BEHALF = "APPOINTMENT_CANCEL_ON_BEHALF";
    public static final String APPOINTMENT_RESCHEDULE_ON_BEHALF = "APPOINTMENT_RESCHEDULE_ON_BEHALF";

    // --- Giữ chỗ khung giờ trong lúc đặt lịch (người thực hiện là bệnh nhân hoặc lễ tân) ---
    public static final String SLOT_HOLD = "SLOT_HOLD";

    // --- Quản trị: tài khoản, danh mục, bác sĩ, lịch làm việc ---
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String CATALOG_CREATE = "CATALOG_CREATE";
    public static final String CATALOG_UPDATE = "CATALOG_UPDATE";
    public static final String CATALOG_DELETE = "CATALOG_DELETE";
    public static final String DOCTOR_CREATE = "DOCTOR_CREATE";
    public static final String DOCTOR_UPDATE = "DOCTOR_UPDATE";
    public static final String DOCTOR_DELETE = "DOCTOR_DELETE";
    public static final String SCHEDULE_CREATE = "SCHEDULE_CREATE";
    public static final String SCHEDULE_UPDATE = "SCHEDULE_UPDATE";
    public static final String SCHEDULE_DELETE = "SCHEDULE_DELETE";
    public static final String DAY_OFF_CREATE = "DAY_OFF_CREATE";
    public static final String DAY_OFF_DELETE = "DAY_OFF_DELETE";

    // --- Hóa đơn và báo cáo ---
    public static final String INVOICE_CREATE = "INVOICE_CREATE";
    public static final String INVOICE_COLLECT = "INVOICE_COLLECT";
    public static final String INVOICE_VOID = "INVOICE_VOID";
    public static final String REPORT_EXPORT = "REPORT_EXPORT";

    // --- Đánh giá lần khám và hồ sơ giới thiệu (V5) ---
    public static final String REVIEW_CREATE = "REVIEW_CREATE";
    public static final String REVIEW_UPDATE = "REVIEW_UPDATE";
    public static final String REVIEW_DELETE = "REVIEW_DELETE";
    public static final String REVIEW_HIDE = "REVIEW_HIDE";
    public static final String REVIEW_SHOW = "REVIEW_SHOW";
    public static final String DOCTOR_PROFILE_UPDATE = "DOCTOR_PROFILE_UPDATE";
    /** Đổi hoặc gỡ ảnh đại diện bác sĩ (bác sĩ tự làm hoặc quản trị viên). */
    public static final String DOCTOR_AVATAR_UPDATE = "DOCTOR_AVATAR_UPDATE";
    /** Đổi hoặc gỡ ảnh chuyên khoa (quản trị viên). */
    public static final String SPECIALTY_IMAGE_UPDATE = "SPECIALTY_IMAGE_UPDATE";

    // --- Truy cập bị từ chối ---
    public static final String ACCESS_DENIED = "ACCESS_DENIED";

    // --- Loại đối tượng (tên bảng) ---
    public static final String ENTITY_USERS = "users";
    public static final String ENTITY_PATIENTS = "patients";
    public static final String ENTITY_MEDICAL_RECORDS = "medical_records";
    public static final String ENTITY_ENCOUNTERS = "encounters";
    public static final String ENTITY_PRESCRIPTIONS = "prescriptions";
    public static final String ENTITY_ATTACHMENTS = "attachments";
    public static final String ENTITY_INVOICES = "invoices";
    public static final String ENTITY_APPOINTMENTS = "appointments";
    public static final String ENTITY_APPOINTMENT_SLOTS = "appointment_slots";
    public static final String ENTITY_SPECIALTIES = "specialties";
    public static final String ENTITY_SERVICES = "services";
    public static final String ENTITY_MEDICINES = "medicines";
    public static final String ENTITY_DOCTORS = "doctors";
    public static final String ENTITY_DOCTOR_SCHEDULES = "doctor_schedules";
    public static final String ENTITY_DOCTOR_DAY_OFFS = "doctor_day_offs";
    public static final String ENTITY_DOCTOR_REVIEWS = "doctor_reviews";
}
