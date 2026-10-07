# MedBook — Hệ thống quản lý bệnh án và đặt lịch khám bệnh

Đồ án học phần CSE702051 — Thiết kế web nâng cao · Nhóm 03 · Đề tài DT03.

Tài liệu này đủ để một người chưa biết dự án dựng và chạy được hệ thống trên máy sạch trong khoảng 30 phút.

## 1. Thông tin dự án

| Nội dung | Thông tin |
|---|---|
| Học phần | CSE702051 — Thiết kế web nâng cao |
| Đơn vị | Khoa Hệ thống thông tin — Trường Công nghệ thông tin — Đại học Phenikaa |
| Nhóm | Nhóm 03 |
| Đề tài | Quản lý bệnh án và đặt lịch khám bệnh (DT03) |
| Repository | https://github.com/hieutruong3705/cse702051-nhom03-medbook |
| Lớp học phần, thành viên, URL trực tuyến | Nhóm trưởng bổ sung |

Dữ liệu trong đồ án là dữ liệu mô phỏng phục vụ học tập.

## 2. Chức năng

Ba vai trò, mỗi vai trò chỉ thấy đúng phần việc của mình (kiểm quyền ở phía máy chủ, không chỉ ẩn nút):

- **Khách:** xem bác sĩ, chuyên khoa, dịch vụ và giờ trống của bác sĩ.
- **Bệnh nhân:** đăng ký, đặt lịch, đổi lịch, hủy lịch; xem bệnh án, đơn thuốc, hóa đơn và thông báo của mình.
- **Bác sĩ:** quản lý ca làm việc, giờ nghỉ, ngày nghỉ (hệ thống tự sinh giờ trống); khám bệnh, ghi bệnh án, kê đơn, tải tệp đính kèm, lập hóa đơn; xem bệnh nhân mình phụ trách.
- **Quản trị viên:** quản lý tài khoản (tạo, khóa, đổi vai trò), hồ sơ bác sĩ, danh mục (chuyên khoa, dịch vụ, thuốc); theo dõi lịch hẹn; thu và hủy hóa đơn; ba báo cáo có biểu đồ và xuất CSV (lịch khám, doanh thu, dịch vụ khám); tra cứu nhật ký hệ thống. Quản trị viên không xem được nội dung bệnh án.

## 3. Công nghệ

| Thành phần | Phiên bản / cách dùng |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1 (Web, Security, Data JPA, Validation), JWT |
| Cơ sở dữ liệu | H2 trong bộ nhớ khi phát triển; MySQL 8 khi chạy Docker hoặc triển khai, lược đồ do Flyway quản lý |
| Giao diện | Vue 3, Vite, Pinia, Tailwind CSS; bản build nằm sẵn trong `src/main/resources/static` |
| Kiểm thử | JUnit 5 + MockMvc (backend), Vitest (giao diện), Playwright (đầu-cuối) |
| Đóng gói | Docker (ba giai đoạn: Node → Maven → JRE) và Docker Compose v2 |

Kiến trúc backend ba tầng Controller → Service → Repository; controller chỉ nhận yêu cầu và gọi một service; mọi lỗi trả về cùng một dạng `ApiError` (`timestamp, status, error, code, message, path, details`).

## 4. Chạy nhanh ở chế độ phát triển (không cần Docker, không cần MySQL)

Yêu cầu: Git và JDK 21. Maven được tải tự động qua `mvnw`.

```powershell
git clone https://github.com/hieutruong3705/cse702051-nhom03-medbook.git
cd cse702051-nhom03-medbook
.\mvnw.cmd spring-boot:run          # Linux, macOS: ./mvnw spring-boot:run
```

Chờ dòng `Started MedbookApplication`, rồi mở **http://localhost:8080/**. Ứng dụng dùng CSDL H2 trong bộ nhớ, tự tạo bảng và nạp dữ liệu mẫu (`src/main/resources/data-h2.sql`); tắt ứng dụng là mất dữ liệu.

Nếu cổng 8080 đang bị chương trình khác dùng, chọn cổng khác:

```powershell
$env:PORT = '8089'; .\mvnw.cmd spring-boot:run      # Linux, macOS: PORT=8089 ./mvnw spring-boot:run
```

Muốn trang báo cáo của quản trị viên có sẵn số liệu và biểu đồ ở chế độ này, đặt thêm `$env:MEDBOOK_SEED_ACTIVITY = 'true'` trước khi chạy (xem mục 6).

### Tài khoản demo

| Tên đăng nhập | Vai trò | Mật khẩu |
|---|---|---|
| `admin1` | Quản trị viên | `MedBook@2026` |
| `doctor1`, `doctor2` | Bác sĩ | `MedBook@2026` |
| `patient1`, `patient2` | Bệnh nhân | `MedBook@2026` |

Có thể đăng nhập bằng email (`admin1@medbook.local`, …). Các tài khoản này chỉ có ở chế độ phát triển và khi bật `MEDBOOK_SEED_DEMO=true` trên Docker; **không bao giờ** có ở hồ sơ `prod`.

## 5. Chạy bằng Docker Compose (MySQL 8)

Yêu cầu: Docker Engine và Docker Compose v2 (trên Windows: Docker Desktop đã khởi động xong).

```powershell
Copy-Item .env.example .env         # Linux, macOS: cp .env.example .env
# Mở .env, đặt MYSQL_ROOT_PASSWORD và JWT_SECRET (chuỗi ngẫu nhiên từ 32 ký tự)
docker compose up -d --build
docker compose ps                   # cột STATUS của app phải là "healthy"
docker compose logs --tail=100 app
```

Mở **http://localhost:8080/**. Lần build đầu cần Internet và mất vài phút.

- Thiếu `MYSQL_ROOT_PASSWORD` hoặc `JWT_SECRET` thì compose dừng và nêu tên biến còn thiếu. Ứng dụng cũng từ chối khởi động nếu `JWT_SECRET` ngắn hơn 32 byte hoặc vẫn là giá trị mẫu.
- Lược đồ do **Flyway** tạo (`src/main/resources/db/migration`), Hibernate chỉ kiểm tra (`ddl-auto: validate`).
- `MEDBOOK_SEED_DEMO=true` (mặc định trong `.env.example`): nạp tài khoản demo ở mục 4 **một lần** khi CSDL còn trống. Kèm theo đó là lịch khám, lần khám và hóa đơn mẫu của 28 ngày gần nhất cho các tài khoản demo, để trang **Quản trị → Báo cáo** có số liệu và biểu đồ ngay; phần này chỉ nạp khi CSDL chưa có lịch hẹn hay hóa đơn nào, tắt riêng bằng `MEDBOOK_SEED_ACTIVITY=false`.
- phpMyAdmin (tùy chọn): `docker compose --profile database up -d`, mở http://localhost:8081/.
- Dừng: `docker compose down`. **Không dùng `down -v`**: tùy chọn `-v` xóa volume `db_data` (toàn bộ CSDL) và `uploads` (tệp đính kèm).

Đã có volume `db_data` dựng từ bản cũ (trước khi có Flyway)? Giữ nguyên mật khẩu cũ trong `.env` rồi `docker compose up -d --build`: Flyway coi lược đồ hiện có là bản V1 và chỉ chạy các migration cộng thêm, dữ liệu được giữ nguyên.

## 6. Biến môi trường

| Biến | Dùng ở | Ý nghĩa |
|---|---|---|
| `PORT` | mọi nơi | Cổng ứng dụng, mặc định 8080 |
| `SPRING_PROFILES_ACTIVE` | mọi nơi | Bỏ trống: phát triển (H2). `docker`, `prod`: MySQL + Flyway. `online`, `v4mysql`: môi trường nghiệm thu đang có |
| `JWT_SECRET` | `docker`, `prod`, `online` | **Bắt buộc.** Khóa ký token, từ 32 byte. Ở chế độ phát triển có thể bỏ trống: ứng dụng tự sinh khóa ngẫu nhiên cho mỗi lần chạy, nên phiên đăng nhập mất hiệu lực khi khởi động lại |
| `JWT_EXPIRATION` | mọi nơi | Thời hạn access token (ms), mặc định 900000 |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE` | compose | Mật khẩu và tên CSDL của container MySQL |
| `MEDBOOK_DB_URL`, `MEDBOOK_DB_USER`, `MEDBOOK_DB_PASSWORD` | `prod`, `online`, `v4mysql` | Kết nối MySQL bên ngoài |
| `MEDBOOK_UPLOAD_DIR` | `docker`, `prod` | Thư mục lưu tệp đính kèm (ngoài thư mục phục vụ web) |
| `MEDBOOK_CORS_ALLOWED_ORIGINS` | `docker`, `prod` | Nguồn được phép gọi API từ trình duyệt, cách nhau bằng dấu phẩy |
| `MEDBOOK_SEED_DEMO` | `docker` | `true`: nạp dữ liệu demo khi CSDL trống. Bị bỏ qua ở `prod` |
| `MEDBOOK_SEED_ACTIVITY` | mọi nơi trừ `prod` | `true`: nạp lịch khám và hóa đơn mẫu 28 ngày gần nhất cho báo cáo khi CSDL chưa có hoạt động. Không đặt thì đi theo `MEDBOOK_SEED_DEMO` |
| `MEDBOOK_OPENAPI_ENABLED` | mọi nơi | `true`: mở `/swagger-ui.html` và `/v3/api-docs`. Mặc định tắt |
| `APP_PORT`, `DB_PORT`, `PMA_PORT` | compose | Cổng trên máy chủ (8080, 3307, 8081) |

Không đưa tệp `.env` lên Git (đã có trong `.gitignore`).

## 7. Kiểm thử

```powershell
.\mvnw.cmd test                      # toàn bộ test backend (khoảng 4 phút)
.\mvnw.cmd test "-Dtest=FlywayMigrationTest"      # chạy một lớp test

cd frontend
npm ci
npx vitest run                       # test giao diện
```

Kiểm thử đầu-cuối bằng Playwright chạy trên ứng dụng thật (cần ứng dụng đang chạy ở chế độ phát triển, ví dụ cổng 8089, và Microsoft Edge có sẵn trên Windows):

```powershell
$env:PORT = '8089'; .\mvnw.cmd spring-boot:run     # cửa sổ thứ nhất
cd frontend; npm run e2e                           # cửa sổ thứ hai (đổi địa chỉ bằng E2E_BASE_URL)
```

Bộ đầu-cuối gồm ba tệp trong `frontend/e2e`: `core-flow.e2e.js` (bác sĩ mở ca, hai bệnh nhân tranh một khung giờ, đặt lịch chỉ bằng bàn phím, khám, kê đơn, tệp đính kèm, hóa đơn, thu tiền, phiên bị thu hồi), `medbook.e2e.js` (trang công khai, phân quyền, quản trị, màn hình hẹp 360 px) và `accessibility.e2e.js` (quét WCAG 2.1 mức A, AA bằng axe trên 29 trang).

Kiểm chứng lược đồ trên MySQL thật (cần Docker; tự dựng một MySQL tạm, không đụng tới container đang chạy):

```powershell
.\scripts\verify-mysql-schema.ps1
```

Đo hiệu năng với hơn 5.000 bản ghi (không nằm trong `mvnw test` thường), kết quả ghi ra `target/performance-report.md`:

```powershell
.\mvnw.cmd test "-Dmedbook.test.excludedGroups=" "-Dgroups=perf"
```

## 8. Phát triển giao diện

```powershell
cd frontend
npm ci
npm run dev        # http://localhost:5173, tự chuyển /api sang backend ở cổng 8080
npm run build      # ghi bản build vào src/main/resources/static (bản backend phục vụ)
```

Sau khi sửa giao diện phải `npm run build` rồi chạy lại backend thì bản ở cổng 8080 mới đổi. Ảnh Docker tự build giao diện nên không cần bước này.

## 9. Cơ sở dữ liệu

| Việc | Cách làm |
|---|---|
| Thêm thay đổi lược đồ | Thêm tệp `V3__mo_ta.sql` (hoặc lớp Java) vào `src/main/resources/db/migration`; chỉ cộng thêm, không sửa migration đã phát hành |
| Nâng cấp CSDL đang chạy với Flyway tắt (hồ sơ `online`, `v4mysql`) | Chạy `scripts/cx-additive-schema.mysql.sql` (chạy lại nhiều lần được) **trước khi** triển khai bản ứng dụng mới |
| Sao lưu | `.\scripts\backup-db.ps1` → tệp `.sql` trong thư mục `backups/` |
| Kiểm chứng phục hồi | `.\scripts\restore-db.ps1 -File <tệp .sql>`: nạp vào một CSDL trống mới rồi đối chiếu số dòng từng bảng |
| Ràng buộc bất biến | `CHECK`, `UNIQUE`, khóa ngoại trong migration; ví dụ giờ kết thúc phải sau giờ bắt đầu, mỗi slot chỉ có một lịch đang giữ chỗ |

## 10. Tài liệu API

Kiểm tra nhanh trạng thái hệ thống (không cần đăng nhập): `GET /api/v1/health`. Mỗi phản hồi có tiêu đề `X-Request-Id`; thân lỗi mang cùng mã đó ở trường `requestId` để đối chiếu với log của máy chủ. Yêu cầu tạo mới trả `201` kèm tiêu đề `Location`.

Đặt `MEDBOOK_OPENAPI_ENABLED=true` rồi mở `/swagger-ui.html`. Bản xuất tĩnh `docs/openapi.json` được sinh lại mỗi lần chạy `OpenApiExportTest`. Mọi đường dẫn nằm dưới `/api/v1`; trừ đăng nhập, đăng ký và các danh mục công khai, yêu cầu phải kèm `Authorization: Bearer <access token>`.

## 11. Xử lý sự cố

| Hiện tượng | Cách xử lý |
|---|---|
| `Port 8080 was already in use` | Chạy với cổng khác: `$env:PORT = '8089'`, hoặc đặt `APP_PORT` trong `.env` khi dùng Docker |
| Compose báo thiếu `JWT_SECRET` hoặc `MYSQL_ROOT_PASSWORD` | Chưa tạo `.env` từ `.env.example`, hoặc chưa điền hai biến đó |
| Ứng dụng dừng với `JWT_SECRET đang là giá trị mẫu` | Thay bằng chuỗi ngẫu nhiên riêng, từ 32 ký tự |
| `Access denied for user 'root'` sau khi đổi mật khẩu trong `.env` | Volume `db_data` đã được tạo với mật khẩu cũ: dùng lại mật khẩu cũ |
| Đăng nhập nhận mã 429 | Một địa chỉ IP đăng nhập sai quá 5 lần trong một phút; chờ hết thời gian ghi trong thông báo |
| Đăng nhập nhận `ACCOUNT_LOCKED` | Tài khoản sai mật khẩu 5 lần liên tiếp (tự mở sau 15 phút) hoặc bị Quản trị viên khóa |
| Giao diện vẫn là bản cũ | Chạy `npm run build` trong `frontend` rồi khởi động lại backend; với Docker: `docker compose up -d --build` |
| Không kết nối được Docker Engine | Mở Docker Desktop, chờ engine sẵn sàng rồi chạy lại `docker info` |
