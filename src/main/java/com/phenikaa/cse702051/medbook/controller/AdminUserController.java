package com.phenikaa.cse702051.medbook.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.user.AdminCreateUserRequest;
import com.phenikaa.cse702051.medbook.dto.user.AdminUserDTO;
import com.phenikaa.cse702051.medbook.dto.user.ChangeUserRolesRequest;
import com.phenikaa.cse702051.medbook.dto.user.ChangeUserStatusRequest;
import com.phenikaa.cse702051.medbook.dto.user.UpdateProfileRequest;
import com.phenikaa.cse702051.medbook.service.AdminUserService;

import jakarta.validation.Valid;

/**
 * Admin quản lý tài khoản (YCCN-05): xem, tạo, sửa, khóa/mở, đổi vai trò. Chỉ ADMIN (xem {@code SecurityConfig}:
 * {@code /admin/**}).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** {@code sort}: {@code id}, {@code username} hoặc {@code createdAt} (mặc định mới tạo trước). */
    @GetMapping
    public PageResponse<AdminUserDTO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return adminUserService.list(keyword, role, status, page, size, sort);
    }

    @PostMapping
    public ResponseEntity<AdminUserDTO> create(@Valid @RequestBody AdminCreateUserRequest request) {
        AdminUserDTO created = adminUserService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public AdminUserDTO get(@PathVariable Long id) {
        return adminUserService.get(id);
    }

    @PutMapping("/{id}")
    public AdminUserDTO update(@PathVariable Long id, @Valid @RequestBody UpdateProfileRequest request) {
        return adminUserService.update(id, request);
    }

    /** Khóa hoặc mở tài khoản. Khóa làm mọi token đã cấp của người đó hết hiệu lực ngay. */
    @PatchMapping("/{id}/status")
    public AdminUserDTO changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeUserStatusRequest request) {
        return adminUserService.changeStatus(id, request);
    }

    /** Thay toàn bộ vai trò của tài khoản. Token cũ (mang vai trò cũ) hết hiệu lực. */
    @PutMapping("/{id}/roles")
    public AdminUserDTO changeRoles(@PathVariable Long id, @RequestBody ChangeUserRolesRequest request) {
        return adminUserService.changeRoles(id, request);
    }
}
