# Phân công backend MedBook cho 2 dev

Tài liệu này chia công việc theo chức năng và nhánh `feature/*`. Mỗi dev triển khai đủ model, repository, service, DTO, controller và kiểm thử cho phần mình phụ trách.

**Trạng thái hiện tại:** có 18 model và 72 file khung tương ứng ở bốn tầng còn lại; các file chưa có nghiệp vụ. Repository hiện chỉ có nhánh local `main`; `develop` và các nhánh dưới đây là kế hoạch cần tạo khi bắt đầu làm việc. Tài liệu không xác nhận chức năng nào đã hoàn thành.

Tên **Dev 1** và **Dev 2** là nhãn phân công, có thể thay bằng tên thành viên. Phạm vi thống nhất là ba vai trò `PATIENT`, `DOCTOR`, `ADMIN` theo báo cáo buổi 2 và lựa chọn của nhóm.

## 1. Phân chia tổng thể

| Người phụ trách | Nhóm chức năng | Số file khung hiện có | Trách nhiệm bổ sung |
|---|---|---:|---|
| **Dev 1** | Tài khoản, phân quyền, hồ sơ bệnh nhân, lịch bác sĩ, khung giờ, đặt lịch, nhật ký | 9 model × 5 tầng = **45 file** | Cấu hình chung, hợp đồng API, báo cáo lịch hẹn, tích hợp bảo mật |
| **Dev 2** | Hồ sơ bác sĩ, chuyên khoa, dịch vụ, thuốc, lượt khám, bệnh án, đơn thuốc, tệp khám | 9 model × 5 tầng = **45 file** | Đặc tả API theo hợp đồng chung, bộ kiểm thử API tích hợp, hồ sơ nghiệm thu |

Mỗi dev làm API phục vụ các vai trò liên quan trong chức năng của mình. Ví dụ Dev 1 làm cả API bệnh nhân đặt lịch và API bác sĩ xem danh sách lịch khám. Số file bằng nhau chỉ giúp dễ kiểm soát phạm vi; khối lượng thực tế cần điều chỉnh theo độ khó của từng PR.

## 2. Quyền phụ trách từng file

Thư mục gốc Java: `src/main/java/com/phenikaa/cse702051/medbook/`.

Với tên model `X`, người được giao phụ trách toàn bộ năm file:

```text
model/X.java
repository/XRepository.java
service/XService.java
dto/XDTO.java
controller/XController.java
```

Ví dụ nhóm `MedicalService` gồm `MedicalService.java`, `MedicalServiceRepository.java`, **`MedicalServiceService.java`**, `MedicalServiceDTO.java`, `MedicalServiceController.java`; giữ đúng tên đang có.

| Model `X` | Người phụ trách | Nhánh triển khai chính |
|---|---|---|
| `User` | Dev 1 | `feature/auth-rbac` |
| `Role` | Dev 1 | `feature/auth-rbac` |
| `UserRole` | Dev 1 | `feature/auth-rbac` |
| `Patient` | Dev 1 | `feature/patient-profile` |
| `DoctorSchedule` | Dev 1 | `feature/doctor-scheduling` |
| `ScheduleBreak` | Dev 1 | `feature/doctor-scheduling` |
| `AppointmentSlot` | Dev 1 | `feature/doctor-scheduling` |
| `Appointment` | Dev 1 | `feature/appointment-booking` |
| `AuditLog` | Dev 1 | `feature/audit-log` |
| `Doctor` | Dev 2 | `feature/doctor-catalog` |
| `Specialty` | Dev 2 | `feature/doctor-catalog` |
| `MedicalService` | Dev 2 | `feature/doctor-catalog` |
| `Medicine` | Dev 2 | `feature/medicine-catalog` |
| `Encounter` | Dev 2 | `feature/medical-records` |
| `MedicalRecord` | Dev 2 | `feature/medical-records` |
| `Prescription` | Dev 2 | `feature/prescriptions` |
| `PrescriptionItem` | Dev 2 | `feature/prescriptions` |
| `Attachment` | Dev 2 | `feature/medical-attachments` |

Một file controller rỗng không đồng nghĩa phải có CRUD công khai cho từng bảng. `UserRole` được quản lý qua chức năng phân quyền; `ScheduleBreak` theo lịch làm việc; `PrescriptionItem` theo đơn thuốc. Nhật ký được ghi từ nghiệp vụ và chỉ cung cấp API tra cứu có phân quyền.

Nếu cần sửa file của người kia, trao đổi trước và tách một PR nhỏ do người phụ trách file rà soát. Không tự đổi tên trường, trạng thái hoặc chữ ký service mà nhánh khác đang sử dụng.

## 3. Danh sách nhánh feature

Các mã F00–F12 dùng để theo dõi phụ thuộc. Cột **phụ thuộc** là điều kiện để tích hợp PR vào `develop`; trong lúc chờ, có thể chuẩn bị thiết kế, DTO và test của phần mình. Mọi nhánh đều bắt đầu ở trạng thái **chưa triển khai**.

| Mã | Dev | Nhánh | Phạm vi và điều kiện nghiệm thu chính | Phụ thuộc |
|---|---|---|---|---|
| F00 | 1 | `feature/backend-foundation` | Chốt quy ước ID/thời gian/DTO/lỗi/phân trang; cấu hình DB và migration cho môi trường phát triển, kiểm thử; khung xử lý lỗi tập trung; thống nhất cách lấy người dùng hiện tại. Chuẩn bị trước cơ chế bảo vệ API nghiệp vụ. | Khung hiện tại đã được commit chung |
| F01 | 1 | `feature/auth-rbac` | `User`, `Role`, `UserRole`; đăng ký, đăng nhập, đăng xuất, đổi/khôi phục mật khẩu, khóa tài khoản; JWT có thời hạn và cơ chế làm mới/thu hồi; băm mật khẩu; kiểm quyền ba vai trò; kiểm tra 401/403. | F00 |
| F02 | 2 | `feature/doctor-catalog` | `Doctor`, `Specialty`, `MedicalService`; quản lý danh mục có phân quyền; tra cứu bác sĩ, lọc theo chuyên khoa/dịch vụ; phân trang; trả đủ dữ liệu để Dev 1 xây lịch. | F00, F01, F03 |
| F03 | 1 | `feature/audit-log` | `AuditLog`; service ghi nhật ký dùng chung và API tra cứu cho người có quyền; nối nhật ký vào đăng nhập, đổi quyền, thao tác nhạy cảm đã có; kiểm tra nội dung nhật ký và quyền truy cập. | F01 |
| F04 | 1 | `feature/patient-profile` | `Patient`; liên kết tài khoản với hồ sơ bệnh nhân; xem/cập nhật hồ sơ theo quyền; chặn truy cập chéo giữa hai bệnh nhân. | F01, F03 |
| F05 | 2 | `feature/medicine-catalog` | `Medicine`; danh mục thuốc cho kê đơn; quản lý bởi Admin, tra cứu bởi bác sĩ; kiểm tra dữ liệu nhập và trạng thái sử dụng. | F01, F03 |
| F06 | 1 | `feature/doctor-scheduling` | `DoctorSchedule`, `ScheduleBreak`, `AppointmentSlot`; ca làm, giờ nghỉ/ngày nghỉ, sinh slot; kiểm tra thời gian chồng lấn; bảo vệ slot đã có lịch hẹn. | F02, F03 |
| F07 | 1 | `feature/appointment-booking` | `Appointment`; đặt/hủy/đổi lịch, tra cứu lịch hẹn, chuyển trạng thái hợp lệ; giữ tính nhất quán khi đổi slot; thử 100 yêu cầu cùng một slot chỉ 1 yêu cầu thành công, các yêu cầu tranh chấp nhận 409. | F04, F06 |
| F08 | 2 | `feature/medical-records` | `Encounter`, `MedicalRecord`; tạo lượt khám từ lịch hẹn, lưu bệnh án; chỉ bệnh nhân sở hữu/bác sĩ phụ trách được đọc; mọi lần đọc thành công có audit; rollback cả trạng thái lịch hẹn khi ghi lượt khám thất bại. | F02, F03, F04, F07 |
| F09 | 2 | `feature/prescriptions` | `Prescription`, `PrescriptionItem`; đơn thuốc theo lượt khám; kiểm tra thuốc/liều lượng/số lượng; lưu đơn và các dòng thuốc trong một giao dịch; bệnh nhân chỉ xem đơn của mình. | F05, F08 |
| F10 | 2 | `feature/medical-attachments` | `Attachment`; tải/lấy tệp theo quyền bệnh án; PDF/JPG/PNG tối đa 10 MB, kiểm chữ ký tệp, tên lưu do hệ thống sinh; tệp ở ngoài thư mục web công khai; xử lý lỗi để tránh metadata hoặc tệp mồ côi. | F08 |
| F11 | 1 | `feature/appointment-reports` | Thêm API cho ba báo cáo: số lượt khám hoàn thành theo ngày, số lượt khám theo bác sĩ/chuyên khoa, tỷ lệ hủy lịch; lọc thời gian và xuất CSV; thống nhất cách tính với Dev 2. Đây là backend, phần biểu đồ giao diện làm ở kế hoạch frontend. | F07, F08 |
| F12 | 2 | `feature/backend-verification` | Bộ API test chạy bằng một lệnh; kiểm thử ba luồng đầu–cuối, bảo mật/quyền bản ghi, đồng thời và hiệu năng; cập nhật OpenAPI/hướng dẫn chạy test, bằng chứng và danh sách lỗi. Dev 1 sửa lỗi thuộc phần mình. | F09, F10, F11 |

Các lớp mới như `AuthController`, bộ xử lý JWT, bộ xử lý lỗi, `ReportController`, DTO request/response hoặc tài liệu OpenAPI sẽ được thêm trong nhánh chức năng tương ứng khi triển khai. Chúng chưa tồn tại chỉ vì được liệt kê trong kế hoạch này.

F01 có thể chia thành hai PR nhỏ trên cùng nhánh khi cần mở đường cho Dev 2: trước hết xác thực/RBAC cơ bản, sau đó hoàn thiện vòng đời token và tài khoản. Không đánh dấu toàn bộ F01 xong khi các mục còn lại chưa được làm.

## 4. Thứ tự triển khai cho hai người

| Đợt | Dev 1 | Dev 2 | Điểm bàn giao |
|---|---|---|---|
| 0 | Chốt baseline, thực hiện F00 | Cùng chốt mô hình quan hệ và hợp đồng; chuẩn bị đặc tả API/danh mục F02 | Có nền chung và tên trường thống nhất |
| 1 | F01 | Chuẩn bị F02 và test theo hợp đồng; tích hợp sau khi phần xác thực cần dùng sẵn sàng | Tài khoản và ba vai trò dùng được |
| 2 | F03 → F04 | Hoàn thiện F02; tiếp tục F05 sau F03 | Bác sĩ, bệnh nhân, dịch vụ, thuốc và nhật ký dùng được |
| 3 | F06 → F07 | Chuẩn bị F08 và cơ chế lưu tệp của F10; kiểm tra hợp đồng với Dev 1 | API lịch hẹn ổn định; đặt đồng thời đúng |
| 4 | Hỗ trợ tích hợp F08, kiểm thử giao dịch lịch hẹn | Tích hợp F08 | Đặt lịch → bắt đầu khám → ghi bệnh án hoạt động |
| 5 | F11; kiểm tra bảo mật phần Dev 1 | F09 → F10 | Đơn thuốc, tệp khám, báo cáo đủ để kiểm thử đầu–cuối |
| 6 | Sửa lỗi của Dev 1; cấu hình và kiểm tra dựng lại | F12, sửa lỗi của Dev 2; tổng hợp bằng chứng | Hai người cùng nghiệm thu và review PR phát hành |

Mỗi dev chỉ nên có một nhánh triển khai chính đang mở. Khi nhánh trước được merge, tạo nhánh tiếp theo từ `develop` mới nhất. Phần đang chờ phụ thuộc có thể chuẩn bị trước, nhưng không merge API trả dữ liệu giả để coi là hoàn thành.

## 5. Hợp đồng giữa hai dev

Các quy ước dưới đây là đề xuất cần chốt trong F00 trước khi viết các model có quan hệ; chưa phải mô tả mã đang chạy.

| Điểm phối hợp | Người cung cấp | Quy ước cần chốt |
|---|---|---|
| Danh tính người dùng | Dev 1 | Cách lấy `userId`/vai trò từ ngữ cảnh xác thực; không tin `userId` hoặc vai trò do client tự gửi để cấp quyền. |
| Model và khóa ngoại | Cả hai, mỗi người sở hữu model đã giao | Kiểu ID thống nhất, tên bảng/cột/FK, nullability và ràng buộc duy nhất. Ưu tiên quan hệ một chiều khi đủ dùng; DTO trao đổi bằng ID, không trả trực tiếp entity JPA. |
| Bác sĩ và dịch vụ | Dev 2 | Định danh bác sĩ/dịch vụ, trạng thái hoạt động, thời lượng khám và quan hệ chuyên khoa; Dev 1 cần các dữ liệu này trước khi sinh slot. |
| Trạng thái lịch hẹn | Dev 1 | Lập bảng chuyển trạng thái, ví dụ `BOOKED → IN_PROGRESS → COMPLETED`, cùng nhánh `CANCELLED` từ trạng thái cho phép. Hai bên thống nhất tên enum trước F07/F08. Chuyển trạng thái sai trả 422. |
| Lượt khám | Dev 2 | Một lịch hẹn có tối đa một lượt khám; bảo vệ bằng ràng buộc duy nhất trên FK lịch hẹn. Chỉ bác sĩ được phân công thực hiện thao tác khám. |
| Quyền bệnh án | Dev 2 | Kiểm tra ở service trên từng bản ghi. Admin không mặc nhiên có quyền đọc bệnh án; bác sĩ ngoài phạm vi phụ trách bị từ chối. |
| Nhật ký | Dev 1 | `AuditLogService` nhận actor, hành động, loại/ID đối tượng, thời gian, IP và kết quả cần ghi. Đối tượng được nhận diện qua loại + ID, không bắt buộc FK đến các bảng nghiệp vụ chưa tạo. Không ghi mật khẩu/token hoặc toàn bộ nội dung bệnh án. Không phụ thuộc ngược service khám/đặt lịch. |
| Lỗi và danh sách | Dev 1 | API dưới `/api/v1`; thống nhất định dạng lỗi, mã 400/401/403/404/409/422 và trường phân trang trước khi Dev 2 viết controller. |
| Thời gian | Cả hai | Thống nhất kiểu dữ liệu và múi giờ lưu; đề xuất lưu mốc thời gian UTC và hiển thị theo `Asia/Bangkok`. Chốt cách diễn giải ngày làm việc/giờ nghỉ trước khi sinh slot. |
| Báo cáo | Dev 1, Dev 2 rà soát | Chốt tiêu chí lượt khám hoàn thành, khoảng ngày, mẫu số tỷ lệ hủy và dữ liệu thử đối chiếu; endpoint báo cáo chỉ trả dữ liệu người gọi có quyền xem. |

### Giao dịch giữa lịch hẹn và lượt khám

`EncounterService` của Dev 2 điều phối bắt đầu/hoàn tất lượt khám và gọi phương thức nghiệp vụ của `AppointmentService` do Dev 1 cung cấp. Có thể thống nhất tên `beginEncounter(appointmentId, actorUserId)` và `completeEncounter(...)`; người gọi lấy actor từ ngữ cảnh xác thực. Hai thao tác tham gia **cùng giao dịch DB**, cùng transaction manager và propagation `REQUIRED`; phương thức cập nhật lịch hẹn không tự tách giao dịch riêng.

Nếu tạo hoặc hoàn tất lượt khám thất bại, thay đổi trạng thái lịch hẹn phải rollback cùng. Việc kiểm tra trạng thái và cập nhật phải an toàn khi có yêu cầu đồng thời. Dev 2 không sửa `AppointmentRepository` trực tiếp; `AppointmentService` cũng không gọi ngược `EncounterService` tạo vòng phụ thuộc.

Dev 1 chịu trách nhiệm cung cấp thao tác đặt/hủy/đổi lịch đóng gói toàn bộ cập nhật slot và appointment. Dev 2 gọi hợp đồng nghiệp vụ, không tự giữ hoặc giải phóng slot.

Với đọc bệnh án, phải kiểm quyền và ghi audit thành công trước khi trả dữ liệu; lỗi ghi audit không được bị bỏ qua. Audit của thao tác ghi thành công được lưu trong cùng giao dịch. Nhật ký đăng nhập thất bại/truy cập bị từ chối cần cơ chế lưu riêng để không mất khi nghiệp vụ rollback; hai dev chốt cách làm trong F03.

Hai người cùng kiểm tra ba ca giao thoa: lỗi ghi audit làm rollback lịch hẹn/lượt khám; hai yêu cầu bắt đầu khám cùng lịch hẹn chỉ tạo một lượt khám; bắt đầu khám cạnh tranh với hủy lịch chỉ chấp nhận một chuyển trạng thái hợp lệ.

## 6. Các file dùng chung và cách hạn chế xung đột

| File/thư mục | Người đầu mối | Quy tắc |
|---|---|---|
| `pom.xml`, `config/SecurityConfig.java`, cấu hình bảo mật/lỗi chung thêm sau | Dev 1 | Dev 2 đề xuất thay đổi bằng PR nhỏ, Dev 1 review trước merge. Không tự mở `permitAll` cho API nghiệp vụ để vượt lỗi tích hợp. |
| `src/main/resources/application*.yml`, `application*.yaml`, `.env.example`, Dockerfile, Compose | Dev 1 | Thống nhất profile và biến môi trường; giữ cấu hình mẫu không chứa bí mật thật. |
| `src/main/resources/db/migration/` | Mỗi dev viết SQL cho bảng mình; Dev 1 điều phối phiên bản | Mỗi thay đổi có migration riêng, đăng ký version/thứ tự tích hợp; không sửa migration đã áp dụng. Kiểm tra FK và thứ tự migration trước merge. |
| Dữ liệu mẫu/seed thêm sau | Mỗi dev chuẩn bị dữ liệu module mình; Dev 1 tích hợp | Dùng dữ liệu giả lập, quy ước ID/thứ tự nạp chung; không cùng sửa một file seed trong hai PR. |
| OpenAPI, tài liệu kiểm thử thêm sau | Dev 2 | Mỗi PR cung cấp nội dung API thay đổi; Dev 2 cập nhật đặc tả, Dev 1 review phần API mình phụ trách. |
| `src/test/java/...` | Dev sở hữu nghiệp vụ | Tách test theo service/controller/nghiệp vụ, có dữ liệu và cách chạy rõ ràng; Dev 2 điều phối test tích hợp chung ở F12. |
| `README.md` | Dev 1 | Gom cập nhật hướng dẫn chạy tại mốc tích hợp; Dev 2 cung cấp hướng dẫn test. |

Tránh format lại toàn bộ dự án trong một PR chức năng. Khi cần thay đổi hợp đồng dùng chung, merge PR hợp đồng trước, rồi hai dev cập nhật các nhánh phụ thuộc.

## 7. Quy trình Git

### Chuẩn bị một lần

Người tích hợp rà soát và commit bộ khung hiện tại trước khi chia nhánh. Workspace đang có cả file khung chưa được theo dõi và các chỉnh sửa cấu hình/README trước đó; chọn rõ file nào thuộc baseline, kiểm tra `git diff --cached` trước khi commit.

Sau khi baseline đã commit và working tree sạch, tạo nhánh tích hợp nếu remote chưa có:

```powershell
git switch main
git pull --ff-only origin main
git switch -c develop
git push -u origin develop
```

Nếu `develop` đã có trên remote, dùng `git fetch origin` rồi `git switch --track origin/develop` khi chưa có nhánh local; nếu đã có local thì `git switch develop` và `git pull --ff-only origin develop`. Hai dev làm trong checkout riêng trên máy của mình.

### Bắt đầu một chức năng

Ví dụ Dev 1 bắt đầu F01 sau khi F00 đã merge:

```powershell
git switch develop
git pull --ff-only origin develop
git switch -c feature/auth-rbac
```

Dev 2 thực hiện tương tự với nhánh được phân công, chẳng hạn `feature/doctor-catalog`. Đổi tên nhánh theo bảng F00–F12; không dùng các tên chung như `feature/dev1` cho toàn bộ học kỳ.

### Commit, đồng bộ và mở PR

```powershell
git status
git add <cac-duong-dan-thuoc-chuc-nang>
git diff --cached
git commit -m "feat(auth): implement login and role checks"
git fetch origin
git merge origin/develop
.\mvnw.cmd test
git push -u origin feature/auth-rbac
```

`<cac-duong-dan-thuoc-chuc-nang>` là chỗ điền các đường dẫn thực tế, không chạy nguyên dòng chứa dấu `< >`. Lệnh test cần môi trường kiểm thử đã cấu hình ở F00; nếu merge có xung đột thì xử lý và hoàn tất merge trước khi chạy test/push. Tài liệu này chỉ cung cấp lệnh hướng dẫn, chưa thực hiện commit, tạo hoặc đẩy nhánh.

Mở Pull Request từ `feature/...` vào `develop`. Người còn lại review và kiểm tra phần giao tiếp với module của mình. Sau khi đáp ứng tiêu chí nghiệm thu, merge PR và tạo nhánh chức năng kế tiếp từ `develop` mới nhất. Khi một mốc ổn định được hai người nghiệm thu, mở PR `develop → main` để phát hành.

## 8. Điều kiện hoàn thành một PR

- Phạm vi rõ ràng: liệt kê chức năng, file chính và các PR phụ thuộc; đủ các tầng cần thiết, không còn logic tạm trả kết quả thành công giả.
- Có validation phía máy chủ, quyền theo vai trò và quyền theo bản ghi; controller gọi service, các thao tác dữ liệu đi qua repository.
- Các thao tác ghi nhiều bảng có giao dịch, ràng buộc DB phù hợp và ca kiểm tra rollback.
- Có test thành công, dữ liệu sai và thiếu quyền của chức năng; bổ sung kiểm thử đồng thời khi thay đổi booking hoặc trạng thái khám.
- Cập nhật API/DTO và migration khi có thay đổi; kiểm tra dựng DB mới và nâng cấp DB đang có theo quy trình dự án.
- Maven test và các kiểm thử tích hợp liên quan chạy thành công; ghi lệnh, điều kiện và kết quả thực tế trong PR.
- Người còn lại đã review; các phụ thuộc trong bảng đã được tích hợp; không có dữ liệu thật hoặc bí mật trong thay đổi.

Mẫu nội dung PR:

```markdown
## Chức năng
- Mã công việc: Fxx
- Hành vi được bổ sung/thay đổi:
- Phụ thuộc PR:

## Phạm vi thay đổi
- API / DTO:
- Model / migration:
- Hợp đồng cần dev còn lại cập nhật:

## Kiểm chứng
- Lệnh và môi trường:
- Kết quả thực tế:
- Trường hợp lỗi / phân quyền / giao dịch:

## Phần còn lại
- Hạn chế hoặc công việc tiếp theo:
```

## 9. Nghiệm thu chung của backend

| Luồng | Dev 1 phụ trách | Dev 2 phụ trách |
|---|---|---|
| Đăng nhập → tìm bác sĩ → chọn slot → đặt/hủy/đổi lịch | Xác thực, bệnh nhân, lịch/slot, booking và chống trùng | Danh mục bác sĩ/chuyên khoa/dịch vụ |
| Lịch hẹn → khám → bệnh án → đơn thuốc | Hợp đồng chuyển trạng thái lịch hẹn, audit dùng chung | Lượt khám, bệnh án, thuốc và đơn thuốc |
| Xem lịch sử → tải kết quả khám → tra cứu báo cáo | Danh tính/quyền nền, audit, báo cáo lịch hẹn | Kiểm quyền bệnh án, lịch sử khám, tệp và test đầu–cuối |

Mốc F12 cần tập hợp tối thiểu 25 ca với kết quả thực tế: đề xuất 9 nghiệp vụ chính, 6 ngoại lệ/biên, 7 phân quyền/bảo mật, 3 báo cáo/xuất dữ liệu. Kiểm thử booking dùng mức 100 yêu cầu đồng thời theo báo cáo nhóm; kiểm tra cả quyền bệnh án và nhật ký mỗi lần xem. Hiệu năng cần dữ liệu tối thiểu 5.000 bản ghi và đo trước/sau trên ít nhất 5 endpoint theo rubric.

Các công việc frontend, biểu đồ hiển thị, khả năng truy cập giao diện và báo cáo học phần đầy đủ cần kế hoạch riêng; bảng trên tập trung vào backend đang được chia việc.

## 10. Căn cứ và giới hạn

- Bộ file model/controller/service/repository/DTO đang có trong repository.
- **Báo cáo buổi 2 nhóm 3:** phạm vi ba vai trò, nghiệp vụ và tiêu chí nhóm đã chọn.
- **Phác thảo danh mục nghiệp vụ V3 MedBook:** nguồn tham khảo bổ sung; phần lễ tân/điều dưỡng và thanh toán không đưa vào phạm vi đã chốt.
- **Tài liệu kỹ thuật hướng dẫn hoàn thiện dự án CSE702051:** nguyên tắc ba tầng, giao dịch, kiểm thử và hồ sơ kỹ thuật.
- **05_BANG-KIEM-VA-RUBRIC_CSE702051.xlsx:** các sheet `Checklist-buoi`, `Rubric-R3`, `Ca-kiem-thu`, `12-rui-ro`; dùng yêu cầu làm mục tiêu, không dùng điểm/trạng thái mẫu làm kết quả MedBook.

Tên nhánh, cách chia 9 model mỗi dev, thứ tự F00–F12 và quy trình review là đề xuất phối hợp cho nhóm; chúng không phải quy định nguyên văn của giảng viên.
