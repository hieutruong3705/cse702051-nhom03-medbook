package com.phenikaa.cse702051.medbook.dto.user;

import java.util.List;

/**
 * Thay toàn bộ vai trò của một tài khoản. Danh sách rỗng hoặc có mã vai trò không tồn tại bị từ chối (400) ở
 * tầng nghiệp vụ, nơi cũng chặn việc tự gỡ quyền ADMIN và gỡ quản trị viên cuối cùng.
 */
public record ChangeUserRolesRequest(
        List<String> roles
) {
}
