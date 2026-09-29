package com.phenikaa.cse702051.medbook.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.LoginRequest;
import com.phenikaa.cse702051.medbook.dto.LoginResponse;
import com.phenikaa.cse702051.medbook.dto.RegisterRequest;
import com.phenikaa.cse702051.medbook.dto.RegisterResponse;
import com.phenikaa.cse702051.medbook.exception.UnauthorizedException;
import com.phenikaa.cse702051.medbook.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * YCCN-01: Đăng ký tài khoản Bệnh nhân mới (kèm băm mật khẩu BCrypt cost 12)
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.registerPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * YCCN-02: Đăng nhập hệ thống, kiểm tra mật khẩu BCrypt và cấp Token xác thực
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * YCCN-03: Đăng xuất, hủy phiên
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new UnauthorizedException("Yêu cầu chưa được xác thực! Vui lòng cung cấp Bearer Token.");
        }
        return ResponseEntity.noContent().build();
    }
}
