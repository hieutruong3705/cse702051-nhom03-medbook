# MedBook — Hệ thống quản lý bệnh án và đặt lịch khám bệnh

Hướng dẫn cài đặt ban đầu — Buổi thực hành 01.

Kế hoạch phát triển: [Phân công backend cho 2 dev và các nhánh feature](docs/PHAN_CONG_BACKEND_2_DEV.md).

## 1. Thông tin dự án

| Nội dung | Thông tin |
|---|---|
| Học phần | CSE702051 — Thiết kế web nâng cao |
| Đơn vị | Khoa Hệ thống thông tin — Trường Công nghệ thông tin — Đại học Phenikaa |
| Nhóm | Nhóm 03 |
| Đề tài | Quản lý bệnh án và đặt lịch khám bệnh (DT03 trong danh mục) |
| Lớp học phần | Chưa bổ sung |
| Nhóm trưởng và thành viên | Chưa bổ sung |
| Repository | https://github.com/hieutruong3705/cse702051-nhom03-medbook |
| URL trực tuyến | Chưa bổ sung URL sau khi triển khai |

## 2. Giới thiệu và phạm vi hiện tại

MedBook hướng tới hỗ trợ quản lý bệnh án và đặt lịch khám. Dự án định hướng tổ chức backend theo ba tầng Controller — Service — Repository và cung cấp API RESTful.

Phạm vi dự kiến gồm ba vai trò:

- **Bệnh nhân:** đặt lịch khám, theo dõi lịch hẹn, xem bệnh án và đơn thuốc của mình.
- **Bác sĩ:** quản lý lịch khám, ghi nhận lượt khám, cập nhật bệnh án và kê đơn.
- **Quản trị viên:** quản lý tài khoản, danh mục, điều phối lịch hẹn và xem báo cáo.

Ở phiên bản hiện tại, ứng dụng mới có trang chào **hello word** tại đường dẫn `/`. Kết nối database đang tạm tắt để kiểm tra khởi động và triển khai trang chào. Các chức năng nghiệp vụ, JWT và tài liệu Swagger chưa được triển khai.

Dữ liệu sử dụng trong đồ án là dữ liệu mô phỏng phục vụ mục đích học tập.

## 3. Công nghệ và phiên bản cấu hình

| Thành phần | Phiên bản / cách sử dụng |
|---|---|
| Java | JDK 21; Docker runtime dùng JRE 21 |
| Spring Boot | 4.1.1 theo `pom.xml` |
| Maven Wrapper | 3.9.16 theo `.mvn/wrapper/maven-wrapper.properties` |
| Maven trong Docker | Nhánh 3.9 theo image `maven:3.9-eclipse-temurin-21-alpine` |
| Frontend | HTML và CSS thuần, được Spring Boot phục vụ |
| Truy cập dữ liệu | Spring Data JPA / Hibernate đã khai báo phụ thuộc; kết nối đang tắt |
| MySQL | 8.0 trong Docker Compose; chưa cần cho trang chào |
| Bảo mật | Spring Security; hiện cho phép truy cập trang chào không đăng nhập |
| Đóng gói | Docker và Docker Compose v2 |
| Quản lý mã nguồn | Git và GitHub |

Bảng trên phản ánh cấu hình dự án. Nhóm cần ghi lại kết quả kiểm chứng phiên bản thực tế trên máy từng thành viên khi nghiệm thu buổi 1.

## 4. Chuẩn bị môi trường

Để chạy bằng Docker, máy cần có Git, Docker Engine và Docker Compose v2. Trên Windows có thể sử dụng Docker Desktop với WSL2; mở Docker Desktop và chờ engine sẵn sàng trước khi chạy lệnh.

Không cần cài riêng Java, Maven hoặc MySQL trên máy nếu chạy bằng Docker. Lần build đầu cần Internet để tải image và thư viện.

Kiểm tra trong terminal:

```shell
 git --version
 docker --version
 docker compose version
 docker info
```

Kết quả mong đợi: các lệnh hiển thị phiên bản; `docker info` kết nối được Docker Engine. Cổng `8080` trên máy phải còn trống.

## 5. Chạy trang chào

### Bước 1. Lấy mã nguồn

```shell
git clone https://github.com/hieutruong3705/cse702051-nhom03-medbook.git
cd cse702051-nhom03-medbook
```

Nếu đã có mã nguồn, mở terminal tại thư mục chứa `Dockerfile`, `docker-compose.yml` và `pom.xml`; không cần clone lại.

### Bước 2. Build và khởi động

```shell
docker compose up -d --build app
docker compose ps
docker compose logs --tail=100 app
```

Chờ nhật ký xuất hiện `Started MedbookApplication`, sau đó mở **http://localhost:8080/**.

Kết quả mong đợi: trang hiển thị tên **MedBook** và dòng **hello word**. Trang chào không yêu cầu đăng nhập hoặc database. Hiện chưa có tài khoản kiểm thử nghiệp vụ.

### Bước 3. Dừng ứng dụng

```shell
docker compose down
```

Lệnh này dừng và gỡ container của dự án, giữ lại volume dữ liệu. Khi sửa mã nguồn, chạy lại lệnh build ở bước 2 để cập nhật ứng dụng.

## 6. Cấu hình và cổng kết nối

| Thành phần | Địa chỉ / cấu hình | Trạng thái hiện tại |
|---|---|---|
| Website cục bộ | `http://localhost:8080/` | Chạy khi container `app` khởi động thành công |
| Cổng ứng dụng | Biến `PORT`, mặc định `8080` | Đọc trong `application.yaml` |
| MySQL từ máy cá nhân | `localhost:3307` | Tạm tắt |
| MySQL từ container ứng dụng | `db:3306` | Tạm tắt |
| phpMyAdmin | `http://localhost:8081/` | Tạm tắt |

Ứng dụng đang loại trừ `DataSourceAutoConfiguration` và đặt `spring.sql.init.mode: never`. Docker Compose đưa MySQL và phpMyAdmin vào profile `database`, nên chúng không khởi động mặc định. Bật container database riêng cũng chưa làm ứng dụng tự kết nối lại; cấu hình ứng dụng cần được khôi phục khi bắt đầu phát triển nghiệp vụ dữ liệu.

Phiên bản trang chào không cần khai báo các biến `DB_*`. Dockerfile đóng gói và chạy file JAR; ứng dụng nhận cổng từ biến `PORT` nếu môi trường triển khai cung cấp.

## 7. Xử lý lỗi ban đầu

| Hiện tượng | Cách kiểm tra và xử lý |
|---|---|
| Không kết nối được Docker Engine | Mở Docker Desktop, chờ engine sẵn sàng rồi chạy lại `docker info` |
| Cổng 8080 đã được sử dụng | Dừng phiên MedBook đang chạy trong IDE hoặc terminal trước khi chạy container |
| Trình duyệt không truy cập được | Kiểm tra `docker compose ps` và `docker compose logs --tail=100 app`; chờ ứng dụng khởi động xong |
| Trang hiển thị nội dung cũ | Chạy lại `docker compose up -d --build app`, sau đó tải lại trình duyệt |
| Vẫn báo lỗi MySQL | Kiểm tra đang chạy bản build mới và cấu hình tạm tắt database còn trong `application.yaml` |

## 8. Thông tin cần bổ sung cho hồ sơ buổi 1

- Mã lớp học phần, danh sách thành viên, nhóm trưởng và phân công V1–V5.
- Kết quả kiểm chứng phiên bản môi trường của từng thành viên.
- URL trang chào trực tuyến qua HTTPS và ảnh minh chứng triển khai.
- Bằng chứng giảng viên có quyền đọc repository.
- Bằng chứng kết nối CSDL trực tuyến khi hoàn thành hạng mục database của buổi 1. Bản trang chào không database hiện tại mới phục vụ chạy thử.
