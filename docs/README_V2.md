# V2 – ĐỀ TÀI 03: QUẢN LÝ BỆNH ÁN VÀ ĐẶT LỊCH KHÁM BỆNH

## 1. Nội dung bàn giao
- `ERD.mmd`: ERD dạng Mermaid.
- `data_dictionary.md`: từ điển dữ liệu.
- `schema.sql`: tạo toàn bộ CSDL từ DB trống.
- `seed.sql`: dữ liệu mẫu giả lập.
- `integrity_checks.sql`: các truy vấn kiểm tra toàn vẹn.
- `test_rebook_after_cancel.sql`: kịch bản chứng minh slot có thể đặt lại sau khi hủy (xem Mục 5).
- `README_V2.md`: hướng dẫn chạy và các điểm cần giải thích.

## 1b. Đã sửa (so với bản trước)
Bảng `appointments` trước đây đặt UNIQUE trực tiếp trên `schedule_id`, khiến một
slot đã có lịch hẹn (kể cả lịch hẹn đã CANCELLED) không thể đặt lại được nữa.
Đã thay bằng cột suy sinh `active_schedule_id` (= `schedule_id` nếu lịch hẹn còn
hiệu lực, NULL nếu CANCELLED/NO_SHOW) và đặt UNIQUE trên cột này. Xem chi tiết ở
Mục 5 của `data_dictionary.md` và chạy `test_rebook_after_cancel.sql` để kiểm chứng.

## 2. Thứ tự chạy
Tạo database:
```sql
CREATE DATABASE clinic_management
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE clinic_management;
```

Sau đó chạy:
1. `schema.sql`
2. `seed.sql`
3. `integrity_checks.sql`
4. (tùy chọn, để lấy ảnh chụp minh chứng) `test_rebook_after_cancel.sql`

Tất cả truy vấn trong `integrity_checks.sql` phải trả về **0 dòng**.

## 3. ERD
Có thể mở `ERD.mmd` bằng Mermaid Live Editor hoặc công cụ hỗ trợ Mermaid để render thành hình ERD.

## 4. Phạm vi dữ liệu
Bản seed chỉ dùng dữ liệu giả lập để phục vụ demo/kiểm thử.

## 5. Lưu ý quan trọng khi tích hợp PHP/API
### Đặt lịch
Không làm kiểu:
```text
SELECT slot -> nếu trống -> INSERT
```
mà không có transaction/locking.

Luồng đề xuất:
```text
BEGIN
  SELECT schedule ... FOR UPDATE
  kiểm tra status = OPEN
  kiểm tra chưa có appointment
  INSERT appointment
COMMIT
```

Nếu bị UNIQUE hoặc tranh chấp đồng thời: trả lỗi nghiệp vụ `409 CONFLICT`.

### Truy cập bệnh án
API phải kiểm tra quyền tại server. Ví dụ:
- patient chỉ xem medical_records.patient_id của mình;
- doctor chỉ xem bản ghi mà doctor_id xuất hiện trong encounters của bệnh án đó;
- mỗi lần đọc phải tạo audit log.

## 6. Các bảng nghiệp vụ tối thiểu của đề tài
`patients`, `medical_records`, `encounters`, `doctors`, `schedules`, `appointments`, `prescriptions`, `services`, `invoices`.

Ngoài ra có:
`prescription_items`, `invoice_items` để chuẩn hóa quan hệ 1-N; và bốn bảng lõi `users`, `roles`, `user_roles`, `audit_logs`.

## 7. Gợi ý bằng chứng khi báo cáo
- Screenshot chạy `schema.sql` thành công trên DB trống.
- Screenshot `seed.sql` chạy thành công.
- Screenshot 12 truy vấn integrity không có dòng kết quả.
- Screenshot ERD.
- Screenshot 2 phiên cùng tranh chấp một slot: một thành công, một nhận 409.
- Screenshot truy cập bệnh án của bệnh nhân khác: 403.
- Screenshot `audit_logs` sau khi bác sĩ xem bệnh án.
