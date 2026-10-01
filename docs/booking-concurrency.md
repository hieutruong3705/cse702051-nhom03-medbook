# MedBook — Chống đặt trùng lịch khám (booking concurrency)

> Phạm vi: BE-03 mục 9 · YCCN-10, 11, 12, 13 · Mã nguồn: `AppointmentService`, `Appointment`, `AppointmentSlot` · Kiểm chứng: `AppointmentConcurrencyTest`, `AppointmentBookingTest`.

## 1. Bài toán

Một khung giờ khám (slot) chỉ được thuộc về **một** lịch hẹn đang hiệu lực. Khi nhiều bệnh nhân bấm "Đặt lịch" vào cùng một slot trong cùng khoảnh khắc, đúng **một** yêu cầu thành công (HTTP 201), các yêu cầu còn lại nhận **409 Conflict** với thông báo rõ ràng, và không có yêu cầu nào gây lỗi 5xx hay để lại dữ liệu nửa vời.

Cách làm cũ (đọc slot → kiểm tra `isAvailable` → ghi) có khoảng hở giữa "đọc" và "ghi": hai giao dịch cùng thấy slot trống rồi cùng ghi, dẫn đến hai lịch trên một slot. Thiết kế mới đóng khoảng hở đó bằng **hai lớp bảo vệ độc lập**.

## 2. Hai lớp bảo vệ

| Lớp | Cơ chế | Chặn được gì | Khi vi phạm |
|---|---|---|---|
| 1 — Khóa lạc quan | `AppointmentSlot.version` (`@Version`). Đặt lịch đổi slot sang `BOOKED` rồi `saveAndFlush` ngay | Hai giao dịch cùng đọc slot ở version N: chỉ giao dịch đầu `UPDATE ... WHERE version = N` thành công (N → N+1), giao dịch sau không khớp dòng nào → `OptimisticLockingFailure` | Bắt trong `takeSlot` → `ConflictException` → **409** |
| 2 — Ràng buộc CSDL | `appointments.active_slot_id` **UNIQUE**; cột này bằng `slot_id` khi lịch còn giữ chỗ và **NULL** khi lịch đã `CANCELLED` | Dù mã ứng dụng sai hoặc có đường ghi khác bỏ qua lớp 1, CSDL vẫn từ chối lịch thứ hai trên cùng slot đang hiệu lực | `DataIntegrityViolationException` → **409** |

Vì UNIQUE cho phép nhiều giá trị NULL, một slot đã hủy **đặt lại được** (lịch cũ có `active_slot_id = NULL`), trong khi hai lịch đang hiệu lực không bao giờ cùng trỏ một slot. Cột được `Appointment.assignSlot(...)`/`cancel(...)` giữ đồng bộ với `status`, nên mã nghiệp vụ không phải tự nhớ cập nhật.

Danh tính bệnh nhân luôn lấy từ JWT (`CurrentActorService`), **không** nhận `patientId` từ request, nên không thể đặt thay người khác.

## 3. Luồng từng thao tác (mỗi thao tác là MỘT giao dịch)

### 3.1 Đặt lịch — `book(slotId, serviceId, notes)`
1. Yêu cầu role `PATIENT`; lấy `patientId` từ JWT.
2. Kiểm tra dịch vụ còn `ACTIVE`; tải slot; kiểm tra slot còn trống, bác sĩ đang nhận lịch, giờ khám chưa qua (sai → 409).
3. **Chiếm slot** (`takeSlot`): `isAvailable=false`, `status=BOOKED`, `saveAndFlush` → lớp 1.
4. Lưu lịch hẹn `BOOKED` với `active_slot_id` = slot, `saveAndFlush` → lớp 2.
5. Phát `AppointmentBookedEvent` (xem mục 5). Trả `201`.

### 3.2 Hủy lịch — `cancel(id, reason)`
Chỉ chủ lịch; chỉ khi `BOOKED` và còn ít nhất `medbook.booking.cancel-before-hours` (mặc định 2) giờ trước giờ khám. Đặt `status=CANCELLED`, `active_slot_id=NULL`, nhả slot (`AVAILABLE`), phát `AppointmentCancelledEvent`.

### 3.3 Đổi lịch — `reschedule(id, newSlotId, reason)` — nguyên tử
Thứ tự **bắt buộc**: (1) chiếm slot MỚI (có kiểm soát xung đột) → (2) nhả slot CŨ → (3) chuyển lịch sang slot mới → flush. Lý do chọn "chiếm mới trước": nếu làm ngược (nhả cũ trước), một lỗi ở bước chiếm slot mới sẽ để bệnh nhân **mất cả hai** slot. Với thứ tự này, mọi thất bại (slot mới vừa bị người khác đặt, vi phạm ràng buộc, lỗi bất kỳ) đều rollback toàn bộ giao dịch: lịch cũ và slot cũ **giữ nguyên**, thông báo 409 nói rõ "lịch hiện tại của bạn được giữ nguyên". Đổi sang chính slot đang giữ → 400 (`newSlotId`).

### 3.4 Chuyển trạng thái khám — `startExamination` / `completeExamination` / `PATCH …/status`
Chỉ bác sĩ phụ trách (`appointment.doctorId` = bác sĩ trong JWT); chỉ theo đúng thứ tự `BOOKED → IN_PROGRESS → COMPLETED`; sai thứ tự → 409. Luồng khám gọi hai API nội bộ này **trong cùng giao dịch** với việc lưu lần khám (xem `EncounterService`), nên lịch và lần khám không bao giờ lệch nhau.

## 4. Máy trạng thái (quyết định D3)

```
BOOKED ──▶ IN_PROGRESS ──▶ COMPLETED
   │
   └──▶ CANCELLED   (chỉ từ BOOKED, chỉ bệnh nhân chủ lịch, trong thời hạn cho phép)
```
`COMPLETED` và `CANCELLED` là trạng thái cuối. Mọi chuyển trạng thái khác → 409.

## 5. Thông báo và sự kiện

Các sự kiện (`AppointmentBookedEvent`, `AppointmentRescheduledEvent`, `AppointmentCancelledEvent`) được phát **trong giao dịch** nhưng chỉ được nghe ở `@TransactionalEventListener(phase = AFTER_COMMIT)`. Giao dịch rollback (ví dụ đặt lịch bị 409) thì **không** có thông báo nào được gửi; thông báo lỗi gửi đi không làm hỏng việc đặt lịch đã commit. Lịch nhắc 24 giờ/2 giờ được xóa dấu `reminder*SentAt` khi đổi lịch để nhắc lại theo giờ mới.

## 6. Hành vi phía giao diện (FE-02)

* **409 khi đặt lịch:** hiện "Khung giờ vừa được người khác đặt", tải lại danh sách slot và **giữ nguyên** các lựa chọn khác (bác sĩ, dịch vụ, ghi chú).
* **409 khi đổi lịch:** giữ nguyên lịch cũ, báo rõ lịch hiện tại không bị ảnh hưởng.
* Nút xác nhận bị khóa khi đang gửi để tránh gửi lặp từ cùng một tab (khóa phía client chỉ là tiện ích; bảo đảm đúng đắn nằm ở mục 2).

## 7. Bằng chứng kiểm thử

| Kiểm thử | Khẳng định |
|---|---|
| `AppointmentConcurrencyTest.hundredConcurrentBookingsOnlyOneSucceeds` | 100 luồng đặt cùng một slot trống → đúng **1** × 201, **99** × 409, **0** lỗi 5xx; slot `BOOKED`, đúng một lịch hiệu lực |
| `…manySlotsEachWithContention` | Nhiều slot, nhiều người tranh mỗi slot → mỗi slot đúng 1 lịch, tổng thành công = số slot |
| `…rescheduleRacesWithNewBooking` | Đổi lịch tranh slot với đặt mới → đúng một bên thắng; bên thua giữ nguyên trạng (lịch cũ còn nguyên nếu là bên đổi lịch) |
| `…databaseRejectsTwoActiveAppointmentsOnSameSlot` | Bỏ qua lớp 1, ghi thẳng hai lịch hiệu lực trên một slot → CSDL từ chối; lịch đã hủy thì ghi được lịch mới |
| `AppointmentBookingTest` (18 ca) | Hủy → đặt lại cùng slot; đổi lịch nguyên tử; hủy/đổi quá hạn → 409; người khác hủy lịch → 403; Admin/bác sĩ không đặt hộ; sự kiện chỉ phát sau commit |

Chạy: `.\mvnw.cmd test -Dtest=AppointmentConcurrencyTest,AppointmentBookingTest`.

## 8. Giới hạn đã biết

* Khóa lạc quan phát hiện xung đột **tại thời điểm flush**, vì vậy `takeSlot` luôn `saveAndFlush`; không đổi thành `save` thường.
* H2 (dev/test) và MySQL (docker) cùng cho kết quả vì cả hai lớp đều dựa trên chuẩn SQL (`UPDATE … WHERE version = ?`, UNIQUE cho phép NULL). Migration MySQL (BE-05) phải tạo UNIQUE trên `appointments.active_slot_id`, **không** dùng UNIQUE phức hợp trên `(slot_id, status)`.
* Chưa có giới hạn tần suất đặt lịch theo bệnh nhân (đề xuất ở `security-evidence.md`).
