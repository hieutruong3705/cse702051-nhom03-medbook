# Bản online riêng để nghiệm thu V4

Mã nguồn: branch `feature/v4-buoi5`. Deploy Dockerfile tại root repository;
frontend production đã nằm trong static resources, cùng origin với API.

## Render Web Service

- Tạo service riêng, không chỉnh service chung của nhóm.
- Chọn repository MedBook, branch feature/v4-buoi5, runtime Docker.
- Chọn Free nếu tài khoản hỗ trợ, không tự chọn gói trả phí.
- Dockerfile path: ./Dockerfile. Health check path: /.
- Tắt auto-deploy trong lúc chốt commit lấy minh chứng.
- Render cấp PORT; mã dùng PORT, không cố định cổng public.

## Biến môi trường

SPRING_PROFILES_ACTIVE=online

MEDBOOK_DB_URL: JDBC URL database MySQL ONLINE riêng. Ví dụ không chứa credential:
`jdbc:mysql://HOST:PORT/DATABASE?sslMode=VERIFY_IDENTITY&serverTimezone=UTC&characterEncoding=UTF-8`
Nếu provider dùng CA riêng, cấu hình truststore theo tài liệu provider; không tắt TLS.
MEDBOOK_DB_USER và MEDBOOK_DB_PASSWORD nhập trực tiếp trong môi trường hosting.
JWT_SECRET: giá trị ngẫu nhiên tối thiểu 32 byte, không dùng secret demo trong repository.
JAVA_TOOL_OPTIONS: `-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Ho_Chi_Minh`

Mặc định MEDBOOK_DDL_AUTO=validate. Với database THỬ MỚI hoàn toàn trống,
đặt update cho lần khởi động đầu để Hibernate tạo schema; sau đó seed một lần
và đổi về validate. Không dùng create/create-drop hoặc database chung đang có dữ liệu.

## Dữ liệu và kiểm chứng

Sau khi schema tạo thành công, nhập scripts/seed-v4-acceptance-mysql.sql vào
database thử mới đã chọn rõ tên. Script không tự chọn database, không chạy lại
trên dữ liệu đã seed. Slot là ngày kế tiếp ngày nhập dữ liệu. Tài khoản và thông tin
bệnh nhân/bác sĩ là dữ liệu giả; mật khẩu demo phục vụ ca thử và không tái sử dụng.
Không nhập scripts/auth-refresh-schema.mysql.sql sau khi Hibernate đã tạo bảng.

Đổi baseUrl của collection Postman sang URL HTTPS của service, slotDate theo
database online. Chạy theo thứ tự; lưu commit triển khai, URL, thời gian, kết quả
và ảnh không chứa token. Kiểm tra sau restart dữ liệu lịch và phiên vẫn lưu trong
MySQL. Upload file chưa có persistent storage online thì không kết luận nghiệm thu
luồng tệp; phạm vi hiện tại là hai luồng Buổi 5.

MySQL online phải sẵn có và truy cập được từ Render. MySQL trên laptop localhost
không thể dùng như URL database online. Render Free web service không cung cấp
MySQL bền vững miễn phí kèm theo; cần database riêng hoặc gói khác được chấp thuận.

Nguồn: https://render.com/docs/web-services ; https://render.com/docs/free ;
https://render.com/docs/deploy-mysql ;
https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html
