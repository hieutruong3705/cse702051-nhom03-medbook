MedBook - Hệ thống Quản lý Bệnh án và Đặt lịch Khám bệnh

 CSE702051 - Thiết kế web nâng cao
Khoa Hệ thống thông tin - Trường Công nghệ thông tin - Đại học Phenikaa

1. Giới thiệu

MedBook là hệ thống web quản lý bệnh án và đặt lịch khám bệnh, được xây dựng theo kiến trúc phân tầng ba lớp (Controller - Service - Repository) và cung cấp API RESTful.

Các nhóm chức năng chính:

- Bệnh nhân: đăng ký, đặt lịch khám, xem bệnh án, xem đơn thuốc.
- Bác sĩ: quản lý lịch khám, ghi bệnh án, kê đơn thuốc.
- Quản trị viên: quản lý người dùng, chuyên khoa, báo cáo thống kê.


2. Công nghệ sử dụng

- Ngôn ngữ: Java 21
- Framework backend: Spring Boot 4.1.1
- Kiến trúc: Layered Architecture (Controller - Service - Repository)
- Cơ sở dữ liệu: MySQL 8.0
- ORM: Spring Data JPA / Hibernate
- Bảo mật: Spring Security, JWT
- Tài liệu API: SpringDoc OpenAPI (Swagger UI)
- Build tool: Maven
- Container: Docker, Docker Compose
- Quản lý mã nguồn: Git, GitHub


3. Yêu cầu hệ thống

Trước khi chạy dự án, cần cài đặt:

- Git
- Docker Desktop (Windows cần bật WSL2)

Không cần cài MySQL, JDK, Maven vì Docker sẽ tự xử lý.

4. Hướng dẫn chạy dự án



