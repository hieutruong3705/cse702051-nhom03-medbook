package com.phenikaa.cse702051.medbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.user.UpdateProfileRequest;
import com.phenikaa.cse702051.medbook.dto.user.UserProfileDTO;
import com.phenikaa.cse702051.medbook.service.UserService;

import jakarta.validation.Valid;

/**
 * Hồ sơ tài khoản của người đang đăng nhập, dùng được cho bệnh nhân, bác sĩ và Admin. Người dùng luôn lấy từ JWT
 * nên không có tham số {@code userId}. Quản trị tài khoản của người khác nằm ở {@link AdminUserController}.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileDTO me() {
        return userService.getMe();
    }

    /** Chỉ sửa được họ tên, điện thoại, email; các trường khác trong body bị bỏ qua. */
    @PutMapping("/me")
    public UserProfileDTO updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateMe(request);
    }
}
