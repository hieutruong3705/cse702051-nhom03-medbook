# Báo cáo thực hành Buổi 6 — Vai trò V4

**Sinh viên:** Nguyễn Đức Mạnh — Nhóm 03 — MedBook, CSE702051.
**Ngày chạy:** 03/10/2026, múi giờ Asia/Saigon.
**Nguồn tiêu chí:** `04_SO-TAY-THUC-HANH-10-BUOI_CSE702051.pdf`, trang 35–38; bảng kiểm V4 ở trang 37. Đã đối chiếu PDF gốc; bản trích ở sổ tay gốc (trang 35–38).

## 1. Kết quả phần kiểm thử

**Đạt kiểm thử local:** 50 yêu cầu song song tranh một slot sức chứa 1 → **1 HTTP 201 Created, 49 HTTP 409 Conflict, 0 lỗi 5xx**. Sau khi hoàn tất, slot `1039` có trạng thái `BOOKED`, đúng một lịch đang hiệu lực và đúng một lịch tổng cộng; không để lại lịch của các yêu cầu thua.

| Chỉ tiêu | Mong đợi | Thực tế | Kết quả |
|---|---:|---:|---|
| Số yêu cầu đồng thời | 50 | 50 | Đạt |
| 201 Created | 1 | 1 | Đạt |
| 409 Conflict | 49 | 49 | Đạt |
| Lỗi 5xx | 0 | 0 | Đạt |
| Lịch hiệu lực trên slot | 1 | 1 | Đạt |
| Tổng lịch trên slot | 1 | 1 | Đạt |

Request số 5 nhận 201 trong lần chạy này. Số request là thứ tự đưa tác vụ vào executor, không phải thứ tự hoàn tất. Kết quả thắng có thể thay đổi ở lần chạy sau.

## 2. Môi trường và cách kiểm chứng

- Nền mã: bản CX-01 trên máy, đã triển khai CX-02 trong thư mục riêng `medbook-cx02`; Git HEAD nền `496557d3dbea07fc4b101fd221e666b5e2dbcb2d`. Các thay đổi đang ở working tree, chưa commit. [SHA-256 file test](minh_chung/source-sha256.txt) ghi nhận mã đã chạy.
- Java 21.0.12, Maven Wrapper; Spring Boot và JUnit của dự án. Dùng cache Maven offline trên máy.
- Test `AppointmentConcurrencyTest.java`, phương thức `hundredConcurrentBookingsOnlyOneSucceeds`; tham số `medbook.concurrency.requests=50`. Mặc định vẫn giữ 100 yêu cầu theo tiêu chí chống đặt trùng của nhóm.
- Executor có 50 worker. `CountDownLatch` đợi tất cả worker sẵn sàng rồi mở cổng cùng lúc; các tác vụ xen kẽ hai bệnh nhân đã xác thực, gửi `POST /api/v1/appointments` với cùng `slotId` và `serviceId=1`.
- MockMvc thực thi security, controller, service, repository với CSDL H2 thật của test; không giả lập service/repository. Đây là kiểm thử tích hợp trong JVM, không phải 50 kết nối HTTP qua localhost và chưa chứng minh hành vi trên MySQL/môi trường trực tuyến.
- Sau phản hồi, test đọc lại CSDL và assert số lịch, trạng thái slot. Các dòng `V4_REQUEST` và `V4_RESULT` chỉ được ghi sau khi các assertion đạt; không ghi token/mật khẩu.

## 3. Lệnh Maven và minh chứng

Lệnh đã chạy từ `medbook-cx02`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot'
.\mvnw.cmd -f ../tmp/cx02-evidence/cx02-runner-pom.xml -o `
  '-Dmaven.repo.local=C:/Users/manhn/Downloads/webadv/tmp/cx01-maven-cache/repository' `
  '-Dmaven.compiler.fork=true' '-Dmaven.main.skip=true' `
  '-Dtest=AppointmentConcurrencyTest#hundredConcurrentBookingsOnlyOneSucceeds' `
  '-Dmedbook.concurrency.requests=50' test
```

POM chạy kiểm chứng sử dụng dependencies của dự án, đường dẫn nguồn tuyệt đối và biên dịch nguồn main cùng test vào output kiểm chứng để chạy trong sandbox hiện tại. POM chính không bị sửa. Không thực hiện `clean` trên các output đang tồn tại. Trên máy không bị hạn chế, có thể chạy bằng POM chính:

```powershell
.\mvnw.cmd '-Dtest=AppointmentConcurrencyTest#hundredConcurrentBookingsOnlyOneSucceeds' '-Dmedbook.concurrency.requests=50' test
```

Lần 50 yêu cầu hoàn tất **21:29:52 +07:00**, Maven exit code **0**, `BUILD SUCCESS`, **1 test / 0 failures / 0 errors / 0 skipped**.

- [Log Maven đầy đủ, UTF-8](minh_chung/maven-50-utf8.log); log gốc UTF-16 giữ trong hồ sơ local.
- [Bảng 50 request CSV](minh_chung/Bang_50_request.csv).
- [Surefire XML của lần 50 yêu cầu](minh_chung/TEST-concurrency-50.xml).
- [Trang minh chứng Ảnh 31](minh_chung/Anh_31.html): hiển thị số liệu và trích nguyên văn log đã chạy; đây là trang trình bày log, không mô phỏng cửa sổ terminal.

**Ảnh PNG chưa xuất được:** công cụ cửa sổ Node/Sky không khởi động được (`path not found`), không có trình duyệt CUA khả dụng. Chrome headless lỗi GPU/không hoàn tất; Firefox headless báo không khởi động được tiến trình tab và không tạo PNG. Không tạo ảnh giả để thay thế. Để hoàn tất mục Ảnh 31, mở `minh_chung/Anh_31.html` bằng trình duyệt trên máy, hoặc mở log Maven thật có dòng `V4_RESULT` cùng `BUILD SUCCESS`, chụp bằng `Win+Shift+S`, lưu thành `minh_chung/Anh_31.png`. Sau khi kiểm tra ảnh hiển thị rõ môi trường, số liệu và nguồn log, mới đánh dấu sản phẩm cá nhân hoàn tất.

## 4. Kiểm thử bổ sung và bất biến CSDL

Chạy lại cả class bằng lệnh trên, thay lựa chọn test bằng `'-Dtest=AppointmentConcurrencyTest'` và bỏ tham số 50. Hoàn tất **21:31:40 +07:00**, exit **0**, **4/4 ca đạt, 100%**, không bỏ qua ca nào. [Log](minh_chung/maven-regression.log), [XML](minh_chung/TEST-concurrency-regression.xml).

| Ca đã chạy | Kiểm tra | Kết quả |
|---|---|---|
| hundredConcurrentBookingsOnlyOneSucceeds | Mặc định 100 yêu cầu, chỉ 1 thành công, 99 xung đột, 0 lỗi 5xx | Đạt |
| manySlotsEachWithContention | 5 slot × 12 yêu cầu: mỗi slot đúng 1 lịch | Đạt |
| rescheduleRacesWithNewBooking | Đổi lịch và đặt mới tranh một slot; đúng một bên thắng, đổi lịch thua giữ lịch cũ | Đạt |
| databaseRejectsTwoActiveAppointmentsOnSameSlot | Chèn trực tiếp lịch thứ hai cùng slot bị DataIntegrityViolationException; hủy lịch đầu rồi đặt lại được | Đạt |

Ca cuối chứng minh ràng buộc CSDL trên H2 chặn vi phạm ngay cả khi đi thẳng qua repository. Các ca giữ chỗ hết hạn và chuyển trạng thái sai trả 422 trong sổ tay là điều kiện theo khung nghiệp vụ; chưa được kiểm chứng trong lượt này, không tính vào số đạt.

## 5. Checklist V4 theo đúng trang 37

- [x] Đã hoàn thành nhiệm vụ: chạy kiểm thử truy cập đồng thời và ghi bảng kết quả. Bằng chứng: log Maven và CSV 50 dòng.
- [ ] Đã có sản phẩm cá nhân: ảnh 31, bảng kết quả 50 yêu cầu song song. CSV và trang HTML đã có; mục ảnh chụp được cập nhật theo kết quả xuất ảnh thực tế ở dưới.
- [ ] Đã đưa phần việc của mình lên repository trong buổi, có lịch sử đóng góp riêng. Chưa commit/push; không tạo lịch sử đóng góp thay cho sinh viên.
- [ ] Đã cập nhật hai chỉ số lên bảng điều khiển: số rủi ro BM1–BM12 đã có bằng chứng; số ca kiểm thử đã chạy và tỷ lệ đạt (%). Chưa thao tác dashboard của nhóm.

Số liệu chuẩn bị để cập nhật dashboard: **4 ca khác nhau đã chạy, 4 đạt, tỷ lệ 100%**; ca tranh chấp cùng slot được chạy thêm cấu hình 50 nên có **5 lượt thực thi**, không tính thành 5 ca khác nhau. Riêng lượt 50 là **1/1 ca đạt**; 49 phản hồi 409 là kết quả đúng mong đợi, không phải 49 test thất bại. Lượt này không kiểm chứng BM1–BM12: **0 rủi ro có bằng chứng mới**, tổng tích lũy của nhóm **chưa đối soát**, không được thay bằng 0/12 hay tự ghi 12/12.

## 6. Phần còn lại để nghiệm thu tại lớp

Phần kiểm thử local đã đạt. Chưa kết luận hoàn tất nghiệm thu Buổi 6 khi chưa có chứng kiến và hoàn tất các mục bàn giao của sổ tay.

1. Mở log, bảng CSV và minh chứng Ảnh 31, chạy lại dưới sự chứng kiến của giảng viên hoặc nhóm trưởng; bổ sung tên người chứng kiến và thời điểm vào nhật ký.
2. Đưa file test, báo cáo và minh chứng vào repository của nhóm theo nhánh được phân công, ghi commit/PR thật vào báo cáo.
3. Cập nhật dashboard: 4 ca / 100%; đối soát bằng chứng hiện hành BM1–BM12 trước khi ghi chỉ số rủi ro.
4. Nếu yêu cầu nghiệm thu trên bản trực tuyến/MySQL, chạy thêm tại đúng môi trường đó và lưu minh chứng riêng.

**Người chứng kiến:** chưa xác nhận.
**Commit/PR bàn giao:** chưa có.
**Dashboard đã cập nhật:** chưa xác nhận.


## Ghi chú bàn giao nhánh v4-buoi6

Commit bàn giao chỉ gồm thay đổi AppointmentConcurrencyTest và hồ sơ trong docs/v4/buoi6. Các thay đổi CX-01/CX-02 khác vẫn ở working tree, không thuộc commit này. Kết quả đã lưu được chạy trên working tree CX-02, không phải một lượt chạy lại từ checkout sạch của commit bàn giao. Các ô checklist phía trên mô tả thời điểm thực nghiệm; xác định commit bàn giao bằng git log trên nhánh v4-buoi6.

Hai XML được loại phần properties chứa cấu hình JVM và đường dẫn máy; giữ nguyên kết quả test và output. Không đưa POM runner với đường dẫn tuyệt đối riêng máy vào repository; lệnh POM chính ở trên dùng để chạy lại. Ảnh PNG, dashboard và người chứng kiến vẫn chưa xác nhận.

Bản log đưa lên Git được chuẩn hóa xuống dòng và bỏ khoảng trắng cuối dòng; bản gốc giữ ở THbuoi6 trên máy.
