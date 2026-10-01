# Kiểm thử V4 Buổi 5 trên MySQL local

Kết quả thực thi: 31 request, 40 assertions, 0 lỗi. Gồm 4 đăng nhập chuẩn bị, 24 ca hai luồng và 3 ca refresh/logout. Xem results.csv hoặc results.json; không chứa token.

## Chạy bằng Postman

1. Import MedBook_V4_Buoi5.postman_collection.json.
2. Kiểm tra baseUrl = http://localhost:8080 và slotDate = ngày có slot trong database. Bộ seed lần đầu dùng ngày kế tiếp ngày nhập SQL.
3. Chạy toàn collection theo thứ tự trong Collection Runner. Các bước tự lấy token, slot và ID từ phản hồi; không nhập ID lịch/encounter thủ công.
4. Xem Test Results và lưu ảnh phản hồi từng ca cần minh chứng. Chạy lại sẽ tạo thêm dữ liệu và dùng hai slot khả dụng tiếp theo; cần đủ hai slot.

## Khởi động ứng dụng

Từ thư mục medbook-v4-candidate, đặt MEDBOOK_DB_URL (JDBC URL của database thử nghiệm), MEDBOOK_DB_USER và MEDBOOK_DB_PASSWORD trong môi trường rồi chạy:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=v4mysql' '-Dspring-boot.run.arguments=--server.port=8080'
```

Database thử nghiệm riêng: medbook_v4_acceptance_20261001. Không dùng database cũ medbook_v4_local. scripts/seed-v4-acceptance-mysql.sql đã được nhập một lần; không nhập lại vào database có dữ liệu. Tài khoản giả lập theo seed dùng mật khẩu MedBook@2026.

## Sửa lỗi phát hiện trong lần chạy đầu

- Audit của thao tác tài khoản ghi cùng giao dịch để không chờ khóa FK trên user. Audit truy cập và từ chối vẫn giữ giao dịch riêng.
- Invoice và InvoiceItem tự điền thời điểm tạo trước khi lưu; Invoice cập nhật updatedAt khi sửa.
- Ca thiếu slotId kiểm tra validation 400; slot không tồn tại kiểm tra 404.

Đây là kết quả API với MySQL local, chưa phải nghiệm thu website/database trực tuyến. Frontend có 177 test tự động đạt. Đã thử giao diện lịch #11: xem chi tiết, đổi giờ 23:00 sang 22:00 ngày 02/10/2026, hủy thành công, đăng xuất và truy cập lại bị chuyển về đăng nhập. Báo cáo chính thức và ảnh minh chứng chưa tạo.

Bổ sung sau kiểm thử giao diện: sửa URL lấy slot thành /appointment-slots/available, thêm menu đặt lịch và GET /medical-services đọc dịch vụ ACTIVE. API dịch vụ trả 200 với 4 dịch vụ trên MySQL.

Kiểm chứng bàn giao: 177 frontend tests đạt. Bộ backend chạy 254 test: 253 đạt, một kỳ vọng cũ GET /medical-services = 404 đã đổi thành 200 khi triển khai API; chạy lại toàn bộ SecurityRoutingTest 84 ca đạt. Các kiểm thử auth/audit 66 ca và encounter 12 ca đã đạt trước đó.
