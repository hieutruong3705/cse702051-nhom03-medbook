# MA TRẬN PHÂN QUYỀN VÀ DANH MỤC CA KIỂM THỬ BẢO MẬT

Tài liệu này đặc tả ma trận phân quyền (RBAC Matrix) và danh mục các ca kiểm thử bảo mật tương ứng cho toàn bộ 25 Yêu cầu chức năng (YCCN-01 đến YCCN-25) của hệ thống MedBook, nhằm ngăn chặn các lỗ hổng bảo mật: **Phá vỡ kiểm soát truy cập (Broken Access Control - BM5)** và **Truy cập chéo đối tượng không an toàn (IDOR - BM4)**.

---

## 1. NGUYÊN TẮC PHÂN QUYỀN VÀ QUY ƯỚC MA TRẬN

### 1.1. Các vai trò người dùng trong hệ thống
1. **`GUEST`**: Người dùng chưa xác thực (khách vãng lai, chưa đăng nhập hoặc không gửi kèm Token).
2. **`PATIENT`**: Bệnh nhân đã đăng ký và đăng nhập hệ thống.
3. **`DOCTOR`**: Bác sĩ khám chữa bệnh.
4. **`ADMIN`**: Quản trị viên hệ thống.

### 1.2. Quy ước ký hiệu trong Ma trận
* **`Có`**: Vai trò được phép thực hiện chức năng/API.
* **`Có (*)`**: Chỉ được phép thao tác trên dữ liệu thuộc quyền sở hữu của chính mình hoặc ca khám do bác sĩ trực tiếp phụ trách (Kiểm soát quyền mức đối tượng – Object-level Permission / Chống IDOR).
* **`Không`**: Bị cấm thực hiện. Phía máy chủ (Backend) bắt buộc phải từ chối và trả về mã lỗi HTTP chuẩn:
  * **`401 Unauthorized`**: Đối với người dùng chưa xác thực (`GUEST`) cố gọi API yêu cầu đăng nhập.
  * **`403 Forbidden`**: Đối với người dùng đã đăng nhập nhưng không đủ quyền hạn vai trò hoặc cố can thiệp dữ liệu của người khác.

---

## 2. MA TRẬN PHÂN QUYỀN RBAC (AUTHORIZATION MATRIX)

| Mã YCCN | Tên chức năng nghiệp vụ | Điểm cuối API dự kiến (RESTful) | Khách (`GUEST`) | Bệnh nhân (`PATIENT`) | Bác sĩ (`DOCTOR`) | Quản trị viên (`ADMIN`) |
|:---:|---|---|:---:|:---:|:---:|:---:|
| **YCCN-01** | Đăng ký tài khoản bệnh nhân | `POST /api/v1/auth/register` | **Có** | **Không** | **Không** | **Không** |
| **YCCN-02** | Đăng nhập hệ thống & cấp JWT | `POST /api/v1/auth/login` | **Có** | **Có** | **Có** | **Có** |
| **YCCN-03** | Đăng xuất & hủy token/phiên | `POST /api/v1/auth/logout` | **Không** | **Có** | **Có** | **Có** |
| **YCCN-04** | Đổi mật khẩu cá nhân | `PUT /api/v1/auth/change-password` | **Không** | **Có (*)** | **Có (*)** | **Có (*)** |
| **YCCN-05** | Khóa / mở khóa tài khoản người dùng | `PATCH /api/v1/users/{id}/status` | **Không** | **Không** | **Không** | **Có** |
| **YCCN-06** | Phân quyền kiểm soát phía server | *(Áp dụng toàn bộ hệ thống)* | — | — | — | — |
| **YCCN-07** | Xem chi tiết bệnh án *(Dữ liệu nhạy cảm)* | `GET /api/v1/medical-records/{id}` | **Không** | **Có (*)** | **Có (*)** | **Không** |
| **YCCN-08** | Tìm kiếm bác sĩ và chuyên khoa | `GET /api/v1/doctors`, `GET /api/v1/specialties` | **Có** | **Có** | **Có** | **Có** |
| **YCCN-09** | Xem khung giờ khám còn trống của bác sĩ | `GET /api/v1/doctors/{id}/slots` | **Có** | **Có** | **Có** | **Có** |
| **YCCN-10** | Đặt lịch khám bệnh | `POST /api/v1/appointments` | **Không** | **Có** | **Không** | **Không** |
| **YCCN-11** | Chống đặt trùng slot (Kiểm soát đồng thời)| *(Khóa dòng Pessimistic Lock)* | — | — | — | — |
| **YCCN-12** | Hủy lịch khám bệnh đã đặt | `PUT /api/v1/appointments/{id}/cancel` | **Không** | **Có (*)** | **Không** | **Có** |
| **YCCN-13** | Đổi lịch khám sang khung giờ khác | `PUT /api/v1/appointments/{id}/reschedule` | **Không** | **Có (*)** | **Không** | **Không** |
| **YCCN-14** | Thiết lập ca làm việc, ngày nghỉ bác sĩ | `POST /api/v1/schedules` | **Không** | **Không** | **Có (*)** | **Có** |
| **YCCN-15** | Xem danh sách bệnh nhân đã đặt lịch | `GET /api/v1/appointments` | **Không** | **Có (*)** | **Có (*)** | **Có** |
| **YCCN-16** | Cập nhật trạng thái khám (Đang khám/Xong)| `PATCH /api/v1/appointments/{id}/status` | **Không** | **Không** | **Có (*)** | **Không** |
| **YCCN-17** | Ghi thông tin bệnh án (chẩn đoán, kết luận)| `POST /api/v1/medical-records` | **Không** | **Không** | **Có (*)** | **Không** |
| **YCCN-18** | Kê đơn thuốc cho ca khám | `POST /api/v1/prescriptions` | **Không** | **Không** | **Có (*)** | **Không** |
| **YCCN-19** | Xem lịch sử khám và đơn thuốc | `GET /api/v1/patients/me/history` | **Không** | **Có (*)** | **Không** | **Không** |
| **YCCN-20** | CRUD Danh mục (Khoa, Bác sĩ, Thuốc) | `POST,PUT,DELETE /api/v1/categories/...` | **Không** | **Không** | **Không** | **Có** |
| **YCCN-21** | Tải tệp bệnh án (ảnh, PDF kết quả) | `POST /api/v1/medical-records/{id}/files` | **Không** | **Không** | **Có (*)** | **Không** |
| **YCCN-22** | Xem báo cáo thống kê quản trị | `GET /api/v1/reports/...` | **Không** | **Không** | **Không** | **Có** |
| **YCCN-23** | Tra cứu nhật ký hệ thống (`audit_logs`) | `GET /api/v1/audit-logs` | **Không** | **Không** | **Không** | **Có** |
| **YCCN-24** | Nhận thông báo xác nhận và nhắc lịch | `GET /api/v1/notifications/my` | **Không** | **Có (*)** | **Có (*)** | **Có (*)** |
| **YCCN-25** | Xem trang thông tin công khai (Landing Page)| `GET /` | **Có** | **Có** | **Có** | **Có** |

---

## 3. DANH MỤC CA KIỂM THỬ PHÂN QUYỀN (CHUYỂN ĐỔI TỪ Ô "KHÔNG")

Tuân thủ nguyên tắc kiểm thử an toàn phần mềm: **Mỗi ô "Không" là một hành vi bị nghiêm cấm**, hệ thống phải từ chối ở tầng máy chủ (không phụ thuộc việc ẩn phần tử trên giao diện). Dưới đây là 16 ca kiểm thử phân quyền tiêu biểu phân theo 3 nhóm rủi ro:

### 3.1. Nhóm 1: Kiểm thử Chưa xác thực (Authentication – Mã HTTP: `401 Unauthorized`)
*Mục đích: Đảm bảo người dùng chưa đăng nhập / không gửi kèm Token không thể truy cập tài nguyên bảo mật.*

| Mã Ca | Tên ca kiểm thử | Căn cứ ô "Không" | Vai trò thực hiện | Endpoint & Method | Dữ liệu / Header gửi lên | Kết quả mong đợi |
|:---:|---|:---:|:---:|---|---|:---:|
| **TC-SEC-01** | Khách chưa đăng nhập cố gọi API Đăng xuất | YCCN-03 | `GUEST` (Không Token) | `POST /api/v1/auth/logout` | `Header: rỗng` | **HTTP 401 Unauthorized** |
| **TC-SEC-02** | Khách chưa đăng nhập cố tình đặt lịch khám | YCCN-10 | `GUEST` (Không Token) | `POST /api/v1/appointments` | `{"doctorId": 1, "slotId": 10}` | **HTTP 401 Unauthorized** |
| **TC-SEC-03** | Khách chưa đăng nhập cố xem lịch sử khám | YCCN-19 | `GUEST` (Không Token) | `GET /api/v1/patients/me/history` | `Header: rỗng` | **HTTP 401 Unauthorized** |
| **TC-SEC-04** | Khách chưa đăng nhập cố xem chi tiết bệnh án | YCCN-07 | `GUEST` (Không Token) | `GET /api/v1/medical-records/1` | `Header: rỗng` | **HTTP 401 Unauthorized** |

---

### 3.2. Nhóm 2: Kiểm thử Phân quyền vai trò chức năng (Functional RBAC – Mã HTTP: `403 Forbidden`)
*Mục đích: Đảm bảo người dùng đã đăng nhập nhưng không có thẩm quyền vai trò phù hợp bị chặn đứng ở phía Backend (Rủi ro BM5).*

| Mã Ca | Tên ca kiểm thử | Căn cứ ô "Không" | Vai trò thực hiện | Endpoint & Method | Dữ liệu / Header gửi lên | Kết quả mong đợi |
|:---:|---|:---:|:---:|---|---|:---:|
| **TC-SEC-05** | Bệnh nhân cố tình khóa tài khoản user khác | YCCN-05 | `PATIENT` | `PATCH /api/v1/users/2/status` | `{"status": "BLOCKED"}` | **HTTP 403 Forbidden** |
| **TC-SEC-06** | Bệnh nhân cố tạo danh mục chuyên khoa mới | YCCN-20 | `PATIENT` | `POST /api/v1/specialties` | `{"name": "Khoa Mắt"}` | **HTTP 403 Forbidden** |
| **TC-SEC-07** | Bệnh nhân cố tình tự tạo ca làm việc bác sĩ | YCCN-14 | `PATIENT` | `POST /api/v1/schedules` | `{"workDate": "2026-10-01"}` | **HTTP 403 Forbidden** |
| **TC-SEC-08** | Bệnh nhân cố tình gọi API tự kê đơn thuốc | YCCN-18 | `PATIENT` | `POST /api/v1/prescriptions` | `{"medicineId": 5, "quantity": 2}`| **HTTP 403 Forbidden** |
| **TC-SEC-09** | Bệnh nhân cố tình xem nhật ký Audit Log | YCCN-23 | `PATIENT` | `GET /api/v1/audit-logs` | `Bearer Token: Patient` | **HTTP 403 Forbidden** |
| **TC-SEC-10** | Bệnh nhân cố tình truy cập báo cáo doanh thu | YCCN-22 | `PATIENT` | `GET /api/v1/reports/revenue` | `Bearer Token: Patient` | **HTTP 403 Forbidden** |
| **TC-SEC-11** | Bác sĩ cố tình gọi API xóa tài khoản người dùng | YCCN-05 | `DOCTOR` | `DELETE /api/v1/users/15` | `Bearer Token: Doctor` | **HTTP 403 Forbidden** |
| **TC-SEC-12** | Admin cố tình tự tạo đơn thuốc y khoa | YCCN-18 | `ADMIN` | `POST /api/v1/prescriptions` | `{"appointmentId": 10}` | **HTTP 403 Forbidden** |

---

### 3.3. Nhóm 3: Kiểm thử Phân quyền mức đối tượng (Chống IDOR – Mã HTTP: `403 Forbidden` hoặc `404 Not Found`)
*Mục đích: Đảm bảo bệnh án và lịch hẹn là dữ liệu riêng tư, người dùng cùng vai trò cũng không được truy cập chéo dữ liệu của nhau (Rủi ro BM4).*

| Mã Ca | Tên ca kiểm thử | Căn cứ ô "Không" | Vai trò thực hiện | Endpoint & Method | Ngữ cảnh kiểm thử | Kết quả mong đợi |
|:---:|---|:---:|:---:|---|---|:---:|
| **TC-IDOR-01** | **Bệnh nhân A xem bệnh án của Bệnh nhân B** | YCCN-07 | `PATIENT` (Tài khoản A) | `GET /api/v1/medical-records/102` | Bản ghi `102` là bệnh án của Bệnh nhân B | **HTTP 403 Forbidden** *(hoặc 404)* |
| **TC-IDOR-02** | **Bệnh nhân A hủy lịch khám của Bệnh nhân B** | YCCN-12 | `PATIENT` (Tài khoản A) | `PUT /api/v1/appointments/205/cancel` | Lịch hẹn `205` thuộc tài khoản Bệnh nhân B | **HTTP 403 Forbidden** |
| **TC-IDOR-03** | **Bác sĩ X xem bệnh án bệnh nhân của Bác sĩ Y** | YCCN-07 | `DOCTOR` (Bác sĩ X) | `GET /api/v1/medical-records/301` | Bản ghi `301` do Bác sĩ Y phụ trách | **HTTP 403 Forbidden** |
| **TC-IDOR-04** | **Bác sĩ X cập nhật trạng thái ca khám Bác sĩ Y**| YCCN-16 | `DOCTOR` (Bác sĩ X) | `PATCH /api/v1/appointments/402/status` | Lịch khám `402` thuộc ca trực của Bác sĩ Y | **HTTP 403 Forbidden** |
