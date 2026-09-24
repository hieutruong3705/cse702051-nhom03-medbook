# Quy uoc ma nguon, du lieu va loi API

## Cau truc ba tang

Backend duoc to chuc theo cac package chinh:

- `controller`: nhan request HTTP, validate input o bien API va goi service.
- `service`: chua xu ly nghiep vu, dieu phoi repository va nem exception co y nghia.
- `repository`: an chi tiet truy cap du lieu hoac nguon du lieu.
- `dto`: kieu du lieu vao/ra API, khong tra truc tiep entity noi bo khi API da on dinh.
- `model`: entity/domain model.
- `exception`: loi API tap trung.
- `config`: cau hinh Spring.

Controller khong goi truc tiep repository. Repository khong xu ly response HTTP. Service la noi quyet dinh nghiep vu va nem cac exception nhu `ResourceNotFoundException`, `BadRequestException`, `ConflictException`.

Flow mau da co san:

```text
GET /api/system/status
SystemStatusController -> SystemStatusService -> SystemStatusRepository
```

## Loi API tap trung

Tat ca loi API di qua `GlobalExceptionHandler` va tra ve JSON thong nhat:

```json
{
  "timestamp": "2026-09-25T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Du lieu dau vao khong hop le",
  "path": "/api/example",
  "details": {}
}
```

Khi viet service, uu tien nem exception rieng cua du an:

- `BadRequestException`: request hop le ve cu phap nhung sai dieu kien nghiep vu.
- `ResourceNotFoundException`: khong tim thay ban ghi/tai nguyen.
- `ConflictException`: trung du lieu, vi pham rang buoc, hoac xung dot trang thai.

Khong nen tu bat loi trong tung controller de tao response loi rieng le, vi viec nay lam API thieu thong nhat.
