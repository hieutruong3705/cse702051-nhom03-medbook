# DATA DICTIONARY – ĐỀ TÀI 03
## Hệ thống Quản lý Bệnh án và Đặt lịch Khám bệnh

> **Phạm vi V2:** thiết kế CSDL, ERD, schema, seed và kiểm tra toàn vẹn.
> Danh sách bảng nghiệp vụ bám theo tài liệu định hướng đề tài 03; bổ sung các bảng lõi và bảng chi tiết cần thiết cho triển khai.

## 1. Bảng tổng quan

| Bảng | Nhóm | Mục đích |
|---|---|---|
| `users` | Core | Tài khoản đăng nhập |
| `roles` | Core | Vai trò hệ thống |
| `user_roles` | Core | Gán người dùng – vai trò |
| `audit_logs` | Core | Nhật ký truy cập/thao tác |
| `patients` | Nghiệp vụ | Hồ sơ bệnh nhân |
| `medical_records` | Nghiệp vụ | Bệnh án của bệnh nhân |
| `encounters` | Nghiệp vụ | Lần khám/đợt điều trị |
| `doctors` | Nghiệp vụ | Hồ sơ bác sĩ |
| `schedules` | Nghiệp vụ | Khung giờ làm việc/đặt lịch |
| `appointments` | Nghiệp vụ | Lịch hẹn |
| `prescriptions` | Nghiệp vụ | Đơn thuốc |
| `prescription_items` | Nghiệp vụ bổ sung | Chi tiết thuốc |
| `services` | Nghiệp vụ | Danh mục dịch vụ khám |
| `invoices` | Nghiệp vụ | Hóa đơn |
| `invoice_items` | Nghiệp vụ bổ sung | Chi tiết hóa đơn |

## 2. Quy ước thiết kế

- Tên bảng: lowercase + snake_case.
- Khóa chính: `id`.
- Khóa ngoại: `<singular_table>_id`.
- Thời gian: `created_at`, `updated_at`; có `deleted_at` khi hệ thống chọn soft delete.
- Tiền: `DECIMAL(12,2)`, không dùng FLOAT/DOUBLE.
- Trạng thái dùng mã ngắn bằng tiếng Anh, không lưu chuỗi trạng thái có dấu.
- Dữ liệu seed là dữ liệu giả lập.

## 3. Chi tiết cột

### `users`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID tài khoản |
| username | VARCHAR(100) | No | — | UNIQUE | Tên đăng nhập |
| password_hash | VARCHAR(255) | No | — | — | Mật khẩu đã băm |
| full_name | VARCHAR(150) | No | — | — | Họ tên hiển thị |
| email | VARCHAR(190) | Yes | NULL | UNIQUE | Email |
| phone | VARCHAR(30) | Yes | NULL | — | Số điện thoại |
| status | VARCHAR(20) | No | ACTIVE | CHECK | ACTIVE/LOCKED/DISABLED |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm tạo |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm cập nhật |

### `roles`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID vai trò |
| code | VARCHAR(50) | No | — | UNIQUE | Mã vai trò |
| name | VARCHAR(100) | No | — | — | Tên vai trò |
| description | VARCHAR(255) | Yes | NULL | — | Mô tả |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm tạo |

### `user_roles`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| user_id | BIGINT UNSIGNED | No | — | PK, FK | Tài khoản |
| role_id | BIGINT UNSIGNED | No | — | PK, FK | Vai trò |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm gán |

### `audit_logs`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID log |
| actor_user_id | BIGINT UNSIGNED | Yes | NULL | FK | Người thực hiện |
| action_code | VARCHAR(50) | No | — | — | Mã hành động, ví dụ MEDICAL_RECORD_VIEW |
| entity_type | VARCHAR(80) | No | — | — | Loại đối tượng |
| entity_id | BIGINT UNSIGNED | Yes | NULL | — | ID đối tượng |
| ip_address | VARCHAR(45) | Yes | NULL | — | IP |
| user_agent | VARCHAR(500) | Yes | NULL | — | User agent |
| metadata_json | JSON | Yes | NULL | — | Thông tin bổ sung |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm log |

### `patients`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID bệnh nhân |
| user_id | BIGINT UNSIGNED | Yes | NULL | UNIQUE, FK | Tài khoản bệnh nhân nếu có |
| patient_code | VARCHAR(30) | No | — | UNIQUE | Mã bệnh nhân |
| full_name | VARCHAR(150) | No | — | — | Họ tên |
| date_of_birth | DATE | No | — | — | Ngày sinh |
| gender_code | VARCHAR(20) | No | — | CHECK | MALE/FEMALE/OTHER |
| phone | VARCHAR(30) | No | — | — | Số điện thoại |
| email | VARCHAR(190) | Yes | NULL | — | Email |
| address | VARCHAR(255) | Yes | NULL | — | Địa chỉ |
| emergency_contact_name | VARCHAR(150) | Yes | NULL | — | Người liên hệ khẩn cấp |
| emergency_contact_phone | VARCHAR(30) | Yes | NULL | — | SĐT khẩn cấp |
| blood_type | VARCHAR(5) | Yes | NULL | — | Nhóm máu |
| allergies | TEXT | Yes | NULL | — | Dị ứng |
| status | VARCHAR(20) | No | ACTIVE | CHECK | ACTIVE/INACTIVE |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm tạo |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm cập nhật |

### `doctors`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID bác sĩ |
| user_id | BIGINT UNSIGNED | No | — | UNIQUE, FK | Tài khoản bác sĩ |
| doctor_code | VARCHAR(30) | No | — | UNIQUE | Mã bác sĩ |
| specialty | VARCHAR(120) | No | — | — | Chuyên khoa |
| license_no | VARCHAR(80) | Yes | NULL | UNIQUE | Số giấy phép mô phỏng |
| years_experience | SMALLINT UNSIGNED | No | 0 | CHECK | Số năm kinh nghiệm |
| bio | TEXT | Yes | NULL | — | Giới thiệu |
| status | VARCHAR(20) | No | ACTIVE | CHECK | ACTIVE/INACTIVE |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm tạo |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm cập nhật |

### `services`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID dịch vụ |
| code | VARCHAR(30) | No | — | UNIQUE | Mã dịch vụ |
| name | VARCHAR(150) | No | — | — | Tên dịch vụ |
| description | VARCHAR(500) | Yes | NULL | — | Mô tả |
| duration_minutes | SMALLINT UNSIGNED | No | — | CHECK > 0 | Thời lượng |
| price | DECIMAL(12,2) | No | 0.00 | CHECK >= 0 | Giá dịch vụ |
| status | VARCHAR(20) | No | ACTIVE | CHECK | ACTIVE/INACTIVE |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm tạo |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm cập nhật |

### `schedules`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID khung giờ |
| doctor_id | BIGINT UNSIGNED | No | — | FK | Bác sĩ |
| schedule_date | DATE | No | — | — | Ngày làm việc |
| start_time | TIME | No | — | CHECK | Giờ bắt đầu |
| end_time | TIME | No | — | CHECK | Giờ kết thúc |
| status | VARCHAR(20) | No | OPEN | CHECK | OPEN/BLOCKED/CLOSED |
| note | VARCHAR(255) | Yes | NULL | — | Ghi chú |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm tạo |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm cập nhật |

### `appointments`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID lịch hẹn |
| appointment_code | VARCHAR(40) | No | — | UNIQUE | Mã lịch hẹn |
| patient_id | BIGINT UNSIGNED | No | — | FK | Bệnh nhân |
| doctor_id | BIGINT UNSIGNED | No | — | FK | Bác sĩ |
| schedule_id | BIGINT UNSIGNED | No | — | UNIQUE, FK | Khung giờ được giữ |
| service_id | BIGINT UNSIGNED | No | — | FK | Dịch vụ |
| appointment_date | DATE | No | — | — | Ngày khám |
| start_time | TIME | No | — | CHECK | Bắt đầu |
| end_time | TIME | No | — | CHECK | Kết thúc |
| reason | VARCHAR(500) | Yes | NULL | — | Lý do khám |
| status | VARCHAR(20) | No | BOOKED | CHECK | BOOKED/CONFIRMED/CHECKED_IN/COMPLETED/CANCELLED/NO_SHOW |
| booked_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm đặt |
| cancelled_at | TIMESTAMP | Yes | NULL | — | Thời điểm hủy |
| cancellation_reason | VARCHAR(500) | Yes | NULL | — | Lý do hủy |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm tạo |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm cập nhật |
| active_schedule_id | BIGINT UNSIGNED | Yes | GENERATED | UNIQUE | Cột suy sinh = schedule_id nếu lịch hẹn còn hiệu lực, NULL nếu CANCELLED/NO_SHOW; dùng để cho phép đặt lại slot sau khi hủy mà không vi phạm UNIQUE |

### `medical_records`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID bệnh án |
| patient_id | BIGINT UNSIGNED | No | — | UNIQUE, FK | Bệnh nhân sở hữu bệnh án |
| record_code | VARCHAR(40) | No | — | UNIQUE | Mã bệnh án |
| blood_type | VARCHAR(5) | Yes | NULL | — | Nhóm máu |
| chronic_conditions | TEXT | Yes | NULL | — | Bệnh mạn tính |
| allergy_notes | TEXT | Yes | NULL | — | Dị ứng |
| medical_history | TEXT | Yes | NULL | — | Tiền sử |
| current_medications | TEXT | Yes | NULL | — | Thuốc đang dùng |
| status | VARCHAR(20) | No | ACTIVE | CHECK | ACTIVE/ARCHIVED |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm tạo |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm cập nhật |

### `encounters`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID lần khám |
| medical_record_id | BIGINT UNSIGNED | No | — | FK | Bệnh án |
| appointment_id | BIGINT UNSIGNED | Yes | NULL | UNIQUE, FK | Lịch hẹn liên quan |
| doctor_id | BIGINT UNSIGNED | No | — | FK | Bác sĩ khám |
| encounter_at | DATETIME | No | — | — | Thời điểm khám |
| chief_complaint | TEXT | Yes | NULL | — | Lý do/triệu chứng chính |
| diagnosis | TEXT | Yes | NULL | — | Chẩn đoán |
| clinical_notes | TEXT | Yes | NULL | — | Ghi chú lâm sàng |
| treatment_plan | TEXT | Yes | NULL | — | Hướng điều trị |
| follow_up_note | TEXT | Yes | NULL | — | Dặn tái khám |
| status | VARCHAR(20) | No | OPEN | CHECK | OPEN/CLOSED |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm tạo |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm cập nhật |

### `prescriptions`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID đơn thuốc |
| encounter_id | BIGINT UNSIGNED | No | — | FK | Lần khám |
| prescription_code | VARCHAR(40) | No | — | UNIQUE | Mã đơn |
| issued_at | DATETIME | No | CURRENT_TIMESTAMP | — | Thời điểm kê |
| notes | VARCHAR(500) | Yes | NULL | — | Ghi chú |
| status | VARCHAR(20) | No | ACTIVE | CHECK | ACTIVE/CANCELLED |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm tạo |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm cập nhật |

### `prescription_items`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID dòng thuốc |
| prescription_id | BIGINT UNSIGNED | No | — | FK | Đơn thuốc |
| medicine_name | VARCHAR(200) | No | — | — | Tên thuốc |
| dosage | VARCHAR(100) | Yes | NULL | — | Liều dùng |
| frequency | VARCHAR(100) | Yes | NULL | — | Tần suất |
| duration_days | SMALLINT UNSIGNED | Yes | NULL | — | Số ngày |
| quantity | DECIMAL(10,2) | No | — | CHECK > 0 | Số lượng |
| instructions | VARCHAR(500) | Yes | NULL | — | Hướng dẫn |

### `invoices`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID hóa đơn |
| invoice_code | VARCHAR(40) | No | — | UNIQUE | Mã hóa đơn |
| patient_id | BIGINT UNSIGNED | No | — | FK | Bệnh nhân |
| appointment_id | BIGINT UNSIGNED | Yes | NULL | UNIQUE, FK | Lịch hẹn |
| subtotal | DECIMAL(12,2) | No | 0.00 | CHECK >= 0 | Tạm tính |
| discount_amount | DECIMAL(12,2) | No | 0.00 | CHECK >= 0 | Giảm giá |
| total_amount | DECIMAL(12,2) | No | 0.00 | CHECK >= 0 | Tổng tiền |
| status | VARCHAR(20) | No | UNPAID | CHECK | UNPAID/PAID/VOID |
| issued_at | DATETIME | Yes | NULL | — | Thời điểm phát hành |
| paid_at | DATETIME | Yes | NULL | — | Thời điểm thanh toán |
| created_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm tạo |
| updated_at | TIMESTAMP | No | CURRENT_TIMESTAMP | — | Thời điểm cập nhật |

### `invoice_items`

| Cột | Kiểu | Null | Mặc định | Ràng buộc | Diễn giải |
|---|---|---:|---|---|---|
| id | BIGINT UNSIGNED | No | AUTO | PK | ID dòng hóa đơn |
| invoice_id | BIGINT UNSIGNED | No | — | FK | Hóa đơn |
| service_id | BIGINT UNSIGNED | Yes | NULL | FK | Dịch vụ |
| description | VARCHAR(255) | No | — | — | Nội dung tính phí |
| quantity | DECIMAL(10,2) | No | 1.00 | CHECK > 0 | Số lượng |
| unit_price | DECIMAL(12,2) | No | 0.00 | CHECK >= 0 | Đơn giá |
| line_total | DECIMAL(12,2) | No | 0.00 | CHECK >= 0 | Thành tiền |

## 4. Quan hệ chính

- `users` N–N `roles` qua `user_roles`.
- `users` 1–0/1 `patients`.
- `users` 1–0/1 `doctors`.
- `doctors` 1–N `schedules`.
- `patients` 1–N `appointments`.
- `doctors` 1–N `appointments`.
- `schedules` 1–0/1 `appointments`: dùng UNIQUE `schedule_id` để bảo toàn một slot.
- `services` 1–N `appointments`.
- `patients` 1–1 `medical_records`.
- `medical_records` 1–N `encounters`.
- `doctors` 1–N `encounters`.
- `encounters` 1–N `prescriptions`.
- `prescriptions` 1–N `prescription_items`.
- `patients` 1–N `invoices`.
- `invoices` 1–N `invoice_items`.
- `audit_logs` ghi người thực hiện và đối tượng được truy cập.

## 5. Điểm cần trình bày khi bảo vệ V2

### Bài toán đặt lịch đồng thời
`appointments.active_schedule_id` (cột suy sinh từ `schedule_id`, NULL khi lịch hẹn đã CANCELLED/NO_SHOW) có UNIQUE. Tầng ứng dụng phải đặt lịch trong transaction và kiểm tra `schedules.status = 'OPEN'`. Khi hai phiên cùng tranh chấp một slot, chỉ một phiên được INSERT thành công; phiên còn lại phải xử lý lỗi xung đột và trả 409. Dùng cột suy sinh thay vì `schedule_id` trực tiếp để khi một lịch hẹn bị hủy, slot đó tự động được giải phóng và có thể đặt lại — nếu đặt UNIQUE thẳng trên `schedule_id`, slot sẽ bị khóa vĩnh viễn sau lần hủy đầu tiên.

### Bài toán bảo mật bệnh án
Không chỉ ẩn nút trên giao diện. API đọc bệnh án phải kiểm tra quyền ở server:
- Bệnh nhân: chỉ đọc `medical_records` của chính mình.
- Bác sĩ: chỉ đọc bệnh án gắn với lần khám mà bác sĩ đó phụ trách.
- Lễ tân/điều dưỡng: không mặc định được đọc nội dung bệnh án nhạy cảm nếu ma trận quyền không cấp.
- Mọi lần đọc bệnh án phải ghi `audit_logs` với `action_code = 'MEDICAL_RECORD_VIEW'`.

### Dữ liệu giả lập
Không đưa dữ liệu bệnh nhân thật vào `seed.sql`.
