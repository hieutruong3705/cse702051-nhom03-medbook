1\. QUY ƯỚC KỸ THUẬT VÀ NGUYÊN TẮC THIẾT KẾ API

1.1. Chuẩn giao thức và định dạng

- Giao thức: HTTPS
- Kiến trúc: RESTful API
- Base URL: /api/v1
- Định dạng trao đổi mặc định: application/json
- Điểm cuối tải tệp (Upload): multipart/form-data

1.2. Xác thực và Phân quyền

- Cơ chế: Token-based authentication với JWT (JSON Web Token).
- Tiêu đề truyền: Authorization: Bearer &lt;access_token&gt;
- Phân vai trò (RBAC):
- PUBLIC: Khách vãng lai, chưa xác thực.
- PATIENT: Bệnh nhân đăng ký và đăng nhập hệ thống.
- DOCTOR: Bác sĩ điều trị.
- ADMIN: Quản trị viên hệ thống y tế.

1.3. Cấu trúc phản hồi chuẩn (Response Format)

Mọi phản hồi thành công hoặc lỗi đều tuân thủ cấu trúc đồng bộ:

// Phản hồi thành công  
{  
  "success": true,  
  "data": { ... },  
  "message": "Thông điệp phản hồi (nếu có)"  
}  
<br/>// Phản hồi phân trang (Pagination)  
{  
  "success": true,  
  "data": \[ ... \],  
  "pagination": {  
    "page": 1,  
    "size": 20,  
    "total_elements": 100,  
    "total_pages": 5  
  }  
}  
<br/>// Phản hồi lỗi chuẩn (thống nhất với V1)  
{  
  "timestamp": "2026-09-23T10:30:00Z",  
  "status": 400,  
  "error": "Bad Request",  
  "message": "Thông báo lỗi chi tiết dành cho lập trình viên/người dùng",  
  "path": "/api/v1/appointments"  
}  
<br/>

2\. BẢNG DANH MỤC ĐIỂM CUỐI API CHI TIẾT (FULL API SPECIFICATIONS)

|     |     |     |     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| STT | Mã YCCN | Method | Endpoint | Quyền (RBAC) | Mô tả chức năng | Tham số / Body | Thành công | Mã lỗi chính |
| I   | NHÓM XÁC THỰC, TÀI KHOẢN & PHÂN QUYỀN (YCCN-01 -> YCCN-06) |     |     |     |     |     |     |     |
| 01  | YCCN-01 | POST | /api/v1/auth/register | PUBLIC | Đăng ký tài khoản Bệnh nhân | Body: email, password, full_name, phone, dob, gender, address | 201 Created | 400, 409 |
| 02  | YCCN-02 | POST | /api/v1/auth/login | PUBLIC | Đăng nhập hệ thống, cấp token JWT | Body: email, password | 200 OK | 400, 401 |
| 03  | YCCN-03 | POST | /api/v1/auth/logout | PATIENT, DOCTOR, ADMIN | Đăng xuất, hủy bỏ phiên làm việc | Header: Authorization | 204 No Content | 401 |
| 04  | YCCN-04 | POST | /api/v1/auth/change-password | PATIENT, DOCTOR, ADMIN | Đổi mật khẩu cá nhân | Body: old_password, new_password | 200 OK | 400, 401 |
| 05  | YCCN-04 | POST | /api/v1/auth/forgot-password | PUBLIC | Gửi email khôi phục mật khẩu | Body: email | 200 OK | 400, 404 |
| 06  | YCCN-06 | GET | /api/v1/users/me | PATIENT, DOCTOR, ADMIN | Lấy thông tin tài khoản hiện tại | Header: Authorization | 200 OK | 401 |
| 07  | YCCN-06 | PUT | /api/v1/users/me | PATIENT, DOCTOR, ADMIN | Cập nhật thông tin tài khoản | Body: full_name, phone, address, avatar_url | 200 OK | 400, 401 |
| 08  | YCCN-05 | PATCH | /api/v1/admin/users/{id}/status | ADMIN | Khóa hoặc kích hoạt tài khoản người dùng | Path: id<br><br>Body: status (ACTIVE/LOCKED), reason | 200 OK | 400, 401, 403, 404 |
| II  | NHÓM THÔNG TIN CÔNG KHAI, CHUYÊN KHOA & TRA CỨU BÁC SĨ (YCCN-08, YCCN-25) |     |     |     |     |     |     |     |
| 09  | \-  | GET | /api/v1/health | PUBLIC | Điểm cuối kiểm tra sức khỏe hệ thống (Health Check) | Không có | 200 OK | 500 |
| 10  | YCCN-25 | GET | /api/v1/public/specialties | PUBLIC | Xem danh sách chuyên khoa | Query: keyword, is_active | 200 OK | 400 |
| 11  | YCCN-25 | GET | /api/v1/public/services | PUBLIC | Danh sách dịch vụ và bảng giá khám | Query: specialty_id, page, size | 200 OK | 400 |
| 12  | YCCN-08 | GET | /api/v1/doctors | PUBLIC, PATIENT, ADMIN | Tìm kiếm, lọc hồ sơ bác sĩ theo điều kiện | Query: keyword, specialty_id, page, size, sort | 200 OK | 400 |
| 13  | YCCN-08 | GET | /api/v1/doctors/{id} | PUBLIC, PATIENT, ADMIN | Xem chi tiết thông tin và tiểu sử bác sĩ | Path: id | 200 OK | 400, 404 |
| III | NHÓM QUẢN LÝ LỊCH TRÌNH, KHUNG GIỜ & ĐẶT KHÁM (YCCN-09 -> YCCN-16) |     |     |     |     |     |     |     |
| 14  | YCCN-14 | POST | /api/v1/doctor/schedules | DOCTOR | Bác sĩ tạo ca trực (tự động phân slot) | Body: work_date, start_time, end_time, slot_duration | 201 Created | 400, 401, 403, 422 |
| 15  | YCCN-14 | GET | /api/v1/doctor/schedules/me | DOCTOR | Bác sĩ xem ca trực và lịch làm việc | Query: from_date, to_date | 200 OK | 401, 403 |
| 16  | YCCN-09 | GET | /api/v1/doctors/{doctorId}/slots | PUBLIC, PATIENT | Xem danh sách khung giờ (slot) còn trống | Path: doctorId<br><br>Query: date | 200 OK | 400, 404 |
| 17  | YCCN-10, 11 | POST | /api/v1/appointments | PATIENT | Đặt lịch khám (Khóa chống đặt trùng slot) | Body: slot_id, doctor_id, service_id, reason, note | 201 Created | 400, 401, 403, 404, 409 |
| 18  | YCCN-19 | GET | /api/v1/patient/appointments | PATIENT | Bệnh nhân xem lịch sử các lượt đặt hẹn | Query: status, page, size | 200 OK | 401, 403 |
| 19  | YCCN-15 | GET | /api/v1/doctor/appointments | DOCTOR | Bác sĩ xem danh sách bệnh nhân đã đăng ký ca | Query: date, status, page, size | 200 OK | 401, 403 |
| 20  | YCCN-12 | PATCH | /api/v1/appointments/{id}/cancel | PATIENT, DOCTOR | Hủy lịch hẹn và giải phóng slot trống | Path: id<br><br>Body: cancellation_reason | 200 OK | 400, 401, 403, 404, 409 |
| 21  | YCCN-13 | PATCH | /api/v1/appointments/{id}/reschedule | PATIENT | Đổi lịch khám sang slot trống mới | Path: id<br><br>Body: new_slot_id, reason | 200 OK | 400, 401, 403, 404, 409 |
| 22  | YCCN-16 | PATCH | /api/v1/appointments/{id}/status | DOCTOR | Cập nhật tiến trình: BOOKED → IN_PROGRESS → COMPLETED | Path: id<br><br>Body: status | 200 OK | 400, 401, 403, 404, 409 |
| IV  | NHÓM BỆNH ÁN, ĐƠN THUỐC & TỆP ĐÍNH KÈM (YCCN-07, 17, 18, 19, 21) |     |     |     |     |     |     |     |
| 23  | YCCN-17 | POST | /api/v1/encounters | DOCTOR | Tạo hồ sơ lần khám (Encounter) và bệnh án | Body: appointment_id, symptoms, diagnosis, treatment_plan, notes | 201 Created | 400, 401, 403, 404, 409 |
| 24  | YCCN-07, 19 | GET | /api/v1/encounters/{id}/record | DOCTOR, PATIENT | Xem chi tiết bệnh án (Chặn IDOR nghiêm ngặt) | Path: id | 200 OK | 401, 403, 404 |
| 25  | YCCN-18 | POST | /api/v1/encounters/{id}/prescriptions | DOCTOR | Bác sĩ kê đơn thuốc cho lần khám | Path: id<br><br>Body: items: \[{medicine_id, dosage, frequency, quantity, usage}\] | 201 Created | 400, 401, 403, 404 |
| 26  | YCCN-07, 19 | GET | /api/v1/encounters/{id}/prescriptions | DOCTOR, PATIENT | Xem chi tiết đơn thuốc | Path: id | 200 OK | 401, 403, 404 |
| 27  | YCCN-21 | POST | /api/v1/encounters/{id}/attachments | DOCTOR | Tải lên kết quả xét nghiệm / hình ảnh X-quang | Path: id<br><br>Multipart: file, attachment_type, description | 201 Created | 400, 401, 403, 413, 415 |
| 28  | YCCN-21 | GET | /api/v1/attachments/{id}/download | DOCTOR, PATIENT | Tải tệp kết quả (Kiểm tra quyền sở hữu IDOR) | Path: id | 200 OK | 401, 403, 404 |
| V   | NHÓM QUẢN TRỊ DANH MỤC DÀNH CHO ADMIN (YCCN-20) |     |     |     |     |     |     |     |
| 29  | YCCN-20 | POST | /api/v1/admin/doctors | ADMIN | Tạo hồ sơ và phân quyền tài khoản Bác sĩ | Body: email, password, full_name, specialty_id, qualification, phone | 201 Created | 400, 401, 403, 409 |
| 30  | YCCN-20 | POST | /api/v1/admin/specialties | ADMIN | Thêm mới chuyên khoa | Body: code, name, description | 201 Created | 400, 401, 403, 409 |
| 31  | YCCN-20 | PUT | /api/v1/admin/specialties/{id} | ADMIN | Cập nhật thông tin chuyên khoa | Path: id<br><br>Body: name, description, is_active | 200 OK | 400, 401, 403, 404 |
| 32  | YCCN-20 | POST | /api/v1/admin/medicines | ADMIN | Thêm mới thuốc vào danh mục nhà thuốc | Body: code, name, active_ingredient, unit, usage_guide | 201 Created | 400, 401, 403, 409 |
| 33  | YCCN-20 | DELETE | /api/v1/admin/medicines/{id} | ADMIN | Vô hiệu hóa (xóa mềm) thuốc khỏi danh mục | Path: id | 204 No Content | 401, 403, 404 |
| VI  | NHÓM THỐNG KÊ, BÁO CÁO & KIỂM TOÁN HỆ THỐNG (YCCN-22, YCCN-23) |     |     |     |     |     |     |     |
| 34  | YCCN-22 | GET | /api/v1/admin/reports/appointments | ADMIN | Thống kê số lượt khám, tỷ lệ hoàn thành/hủy | Query: from_date, to_date, group_by | 200 OK | 400, 401, 403 |
| 35  | YCCN-22 | GET | /api/v1/admin/reports/doctors-performance | ADMIN | Thống kê hiệu suất bác sĩ theo chuyên khoa | Query: from_date, to_date, specialty_id | 200 OK | 400, 401, 403 |
| 36  | YCCN-22 | GET | /api/v1/admin/reports/export | ADMIN | Xuất dữ liệu thống kê ra file Excel/CSV | Query: report_type, from_date, to_date | 200 OK | 400, 401, 403 |
| 37  | YCCN-23 | GET | /api/v1/admin/audit-logs | ADMIN | Tra cứu nhật ký kiểm toán (Audit Logs) | Query: actor_id, action, target_table, from_date, to_date, page, size | 200 OK | 401, 403 |

3\. CÁC NGUYÊN TẮC KIỂM SOÁT LỖI NGHIỆP VỤ & BẢO MẬT

1.  Kiểm soát Tranh chấp Đồng thời (409 Conflict):
    - Áp dụng kỹ thuật Pessimistic Locking (SELECT FOR UPDATE) hoặc Optimistic Locking (version column) tại điểm cuối POST /api/v1/appointments.
    - Nếu slot khám đã chuyển trạng thái BOOKED trước khi giao dịch commit, hệ thống phải rollback và trả về ngay mã 409 Conflict.
2.  Phòng chống Lỗ hổng Tham chiếu Đối tượng Trực tiếp (IDOR - 403 Forbidden):
    - Tại các điểm cuối xem thông tin nhạy cảm: GET /api/v1/encounters/{id}/record và GET /api/v1/attachments/{id}/download.
    - Tầng Service phải xác thực: Mã patient_id trong bệnh án có khớp với id của token gọi vào hay không (nếu là bệnh nhân), hoặc doctor_id có phụ trách ca khám này hay không (nếu là bác sĩ). Nghiêm cấm trả dữ liệu khi chỉ dựa vào ID truyền qua URL.
3.  Kiểm soát Giới hạn và Định dạng Tệp Tải lên (413 Payload Too Large, 415 Unsupported Media Type):
    - Chỉ cho phép tệp dạng: image/jpeg, image/png, application/pdf.
    - Dung lượng tối đa: 5MB/tệp. Mọi vi phạm bị chặn ngay tại middleware trước khi đẩy vào lưu trữ.

