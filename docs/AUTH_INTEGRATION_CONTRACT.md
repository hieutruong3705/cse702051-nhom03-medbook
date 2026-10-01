# Hợp đồng tích hợp auth cho bản nghiệm thu V4

Ngày chốt thiết kế: 01/10/2026. Trạng thái: thiết kế để triển khai, chưa phải chức năng đã chạy.

Nền leader: `6e42e52971238978f262f525f5f7615312109f01`.
Nguồn tham khảo local Dev 1: `0189efc47719db953749aed9b7a11630095480d8`.
Nhánh triển khai: `integration/v4-auth-refresh`.
Mốc kiểm chứng nền: 236 test backend đạt trên H2; chưa chứng minh bản tích hợp hoặc MySQL đạt.

## 1. Quyết định giữ và bổ sung

- Giữ JwtUtil, JwtAuthenticationFilter, CurrentUserService, CurrentActorService và định dạng lỗi ApiError của leader. Không đưa thêm JwtDecoder/filter thứ hai.
- Giữ đăng ký tạo Patient và MedicalRecord trong cùng giao dịch, kiểm tra quyền, audit và nghiệp vụ của leader.
- Giữ token_version và danh sách thu hồi jti. Bổ sung phiên đăng nhập bền vững cùng refresh token từ ý tưởng của TokenService local; không sao chép nguyên lớp.
- Giữ tên trường token trong LoginResponse để frontend hiện tại tiếp tục hiểu; thêm refreshToken và refreshExpiresAt.
- Access token mặc định 15 phút; phiên refresh có hạn tuyệt đối 7 ngày từ lúc đăng nhập, không kéo dài qua mỗi lần refresh. Cấu hình được. Access expiry không vượt session expiry.
- Mỗi lần đăng nhập tạo một phiên riêng. Logout thu hồi phiên hiện tại; đổi/reset mật khẩu, khóa tài khoản và đổi role tiếp tục dùng token_version để vô hiệu toàn bộ phiên cũ.
- Khóa tạm do đăng nhập sai tiếp tục theo hành vi leader: chặn đăng nhập mới. Không tự đổi sang thu hồi phiên đang dùng chỉ vì bị người khác thử sai mật khẩu.

## 2. Hợp đồng HTTP

Tất cả endpoint dưới `/api/v1/auth`. Token chỉ truyền qua HTTPS khi triển khai trực tuyến. Local dùng HTTP. Không ghi token hoặc mật khẩu vào log/audit.

| Endpoint | Đầu vào | Thành công | Lỗi chính |
|---|---|---|---|
| POST /login | JSON usernameOrEmail, password như cũ | 200, LoginResponse hiện tại + refreshToken, refreshExpiresAt | Giữ mã lỗi hiện hành của leader |
| POST /refresh | JSON refreshToken, không cần access token | 200: token, tokenType=Bearer, expiresAt, refreshToken, refreshExpiresAt | 400 thiếu/sai định dạng; 401 token không hợp lệ, đã dùng, phiên hết hạn/thu hồi hoặc phiên bản tài khoản thay đổi |
| POST /logout | Bearer access token còn hợp lệ | 204, thu hồi cả phiên và jti hiện tại | 401 nếu chưa xác thực |
| POST /change-password | Giữ body hiện tại | 204, mọi phiên cũ không dùng/refresh được | Giữ lỗi hiện hành |
| POST /forgot-password | Giữ body hiện tại | 202 như cũ | Giữ lỗi hiện hành |
| POST /reset-password | Giữ body hiện tại | 204, mọi phiên cũ không dùng/refresh được | Giữ lỗi hiện hành |

expiresAt và refreshExpiresAt là ISO-8601 UTC. LoginResponse tiếp tục có thông tin user/role/patient/doctor. Refresh không thay đổi danh tính người dùng; lấy role mới từ CSDL khi phát hành JWT, sau khi kiểm tra token_version.

Refresh là endpoint công khai đối với access-token filter nhưng tự xác thực bằng refresh token. Phản hồi login/refresh thêm Cache-Control: no-store. Lỗi 401 dùng ApiError hiện tại, không tiết lộ nguyên nhân nội bộ; audit chỉ ghi mã phiên/user và loại sự kiện.

Logout khi access hết hạn: frontend refresh một lần rồi gọi logout với access mới. Nếu refresh bị từ chối, xóa trạng thái đăng nhập. Nếu mất mạng, không tuyên bố server đã thu hồi phiên; hiển thị lỗi và cho thử lại.

## 3. Dữ liệu mới

Tên bảng dự kiến: auth_sessions và refresh_tokens. Phải đối chiếu database đích trước khi migration, đặc biệt khi bảng cùng tên từ local Dev 1 đã tồn tại.

| Bảng/cột | Kiểu logic | Ràng buộc và ý nghĩa |
|---|---|---|
| auth_sessions.id | UUID dạng chuỗi 36 ký tự | Khóa chính; claim sid trong JWT |
| user_id | BIGINT | NOT NULL, FK users.id, index |
| token_version | INTEGER | NOT NULL; bản sao users.token_version lúc đăng nhập |
| created_at | thời điểm UTC | NOT NULL |
| expires_at | thời điểm UTC | NOT NULL, index dọn dữ liệu |
| revoked_at | thời điểm UTC, nullable | Có giá trị nghĩa là phiên bị thu hồi |
| refresh_tokens.token_hash | CHAR(64) | Khóa chính; SHA-256 của token ngẫu nhiên, không lưu token thô |
| session_id | VARCHAR(36) | NOT NULL, FK auth_sessions.id, index |
| created_at | thời điểm UTC | NOT NULL |
| used_at | thời điểm UTC, nullable | Đánh dấu đã đổi lấy token mới |

Refresh token gồm session ID và 32 byte ngẫu nhiên mã hóa base64url. Session ID chỉ dùng tìm phiên; không chứng minh quyền. Phải kiểm hash và quan hệ session_id trước khi thu hồi do phát hiện dùng lại, tránh ai đoán sid cũng có thể khóa phiên người khác.

Giữ các token đã dùng đến khi phiên hết hạn để phát hiện replay. Tác vụ dọn xóa refresh_tokens trước auth_sessions, theo đợt, chỉ sau expires_at. Không xóa bằng yêu cầu client.

JWT giữ sub=username, userId, roles, ver, jti, iat, exp của leader; thêm sid. SessionService kiểm tra chữ ký/hạn qua JwtUtil, tài khoản ACTIVE, username, ver, jti và sid thuộc đúng user, chưa thu hồi, chưa hết hạn. Token thiếu sid bị từ chối sau chuyển đổi; yêu cầu đăng nhập lại, không duy trì đường bỏ qua kiểm tra phiên cho token cũ. Test helper phải tạo phiên thật thay vì chỉ ký JWT.

## 4. Giao dịch và xử lý tranh chấp

1. Refresh khóa dòng user trước rồi dòng phiên; áp dụng cùng thứ tự khóa ở các thao tác cần cả hai. Kiểm token_version/status trong giao dịch để phối hợp đổi mật khẩu, reset và thay đổi quyền.
2. Tìm hash; kiểm phiên tương ứng. Token chưa dùng: đánh dấu used_at, tạo refresh mới, phát access mới; commit tất cả hoặc rollback tất cả.
3. Token đã dùng: ghi revoked_at của cả phiên và trả 401. Việc thu hồi phải được commit dù phản hồi là lỗi; có test chống rollback nhầm.
4. Hai refresh đồng thời với cùng token: tối đa một lần cấp mới; lần dùng lại thu hồi phiên. Frontend phải tránh phát hai yêu cầu như vậy. Không có khoảng miễn trừ replay.
5. Logout khóa phiên và thu hồi; request dùng access cũ sau commit phải bị từ chối. Giao dịch đã được xác thực và đang chạy trước thời điểm logout không được cam kết hủy hồi tố.

## 5. Frontend

- Giữ API client và auth store hiện có; bổ sung refresh trước khi hết hạn và một lần thử lại khi API bảo vệ trả 401. Không refresh khi 403 hoặc login sai.
- Loại login/register/refresh/forgot/reset khỏi xử lý 401 tự động; đánh dấu request đã retry để không lặp vô hạn.
- Các request trong cùng tab dùng chung một Promise refresh. Xử lý nhiều tab bằng Web Locks cùng thông báo cập nhật token qua storage/BroadcastChannel; tab đã có token mới thì không dùng lại token cũ. Trình duyệt không hỗ trợ phối hợp thì giới hạn phiên được lưu trong sessionStorage theo tab và yêu cầu đăng nhập lại khi chuyển chế độ.
- Giai đoạn tích hợp này giữ cơ chế bearer/JSON hiện tại; không thêm cookie đồng thời. Nếu lưu refresh cùng localStorage như access hiện tại, phải ghi nhận khả năng bị lấy token khi có XSS trong đánh giá bảo mật. Không coi cách lưu đó đã giải quyết XSS.
- Đổi/reset mật khẩu xong yêu cầu đăng nhập lại. Khi refresh 401, xóa toàn bộ token và chuyển về login. Lỗi mạng không tự gửi lại refresh đã có thể được server xử lý; yêu cầu đăng nhập lại nếu không xác định được kết quả rotation.

## 6. Mật khẩu và cấu hình

- Giữ chính sách hiện hành và bổ sung giới hạn 72 byte UTF-8 của BCrypt cho đăng ký, đổi và reset. Dùng validator chung, trả lỗi trường 400; test tiếng Việt/nhiều byte và đúng ranh giới.
- Access TTL đề xuất: jwt.expiration-ms=900000. Refresh TTL đề xuất: medbook.security.refresh-expiration-ms=604800000. Kiểm giá trị dương khi khởi động.
- Không tái sử dụng secret demo cho môi trường trực tuyến. Không đổi cấu trúc API nghiệp vụ hoặc schema lịch khám trong công việc auth này.

## 7. Migration và chạy lại

- Tạo SQL migration MySQL rõ phiên bản và schema tương ứng H2; không bật Flyway đột ngột trên database hiện có khi chưa có baseline.
- Kiểm tra trên database thử mới trước. Không nhập vào medbook_db hay medbook_v4_local trong bước thiết kế.
- Với CSDL local có auth_sessions/refresh_tokens cũ: phải viết migration điều chỉnh cột/token_version và thu hồi phiên cũ, không dùng CREATE TABLE IF NOT EXISTS để che schema lệch.
- Thử rollback bằng quay lại mã nền và database thử riêng; không hứa token trước chuyển đổi còn dùng được.

## 8. Thứ tự triển khai và kiểm chứng

1. Schema/entity và validator mật khẩu; test ranh giới.
2. Phiên trong login/JWT/filter/logout và cập nhật test helper; test token thiếu/sai sid, phiên hết hạn/thu hồi, user/version không khớp.
3. Refresh rotation, replay và giao dịch đồng thời; test hash-only, logout chặn cả access/refresh, reset/đổi quyền/khóa rồi mở lại không hồi sinh phiên.
4. Frontend refresh, retry, lỗi mạng và phối hợp nhiều tab; chạy test API client/store hiện có và test mới.
5. Chạy lại hai luồng: tìm slot–đặt–đổi/hủy–đặt lại và khám–kê đơn–hóa đơn; test người khác truy cập bị chặn.
6. Build sạch + toàn bộ test backend; frontend test/build; kiểm chứng MySQL riêng cho transaction/khóa. Thu kết quả Postman trên bản đã chốt; H2 không thay cho MySQL hoặc môi trường trực tuyến.

Không đánh dấu các bước triển khai đạt từ tài liệu này. Mỗi thay đổi cần kết quả test và commit riêng; không push hoặc gộp develop trong bước thiết kế.
