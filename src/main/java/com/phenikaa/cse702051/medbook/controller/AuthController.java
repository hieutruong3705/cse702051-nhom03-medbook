package com.phenikaa.cse702051.medbook.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.config.JwtAuthenticationFilter;
import com.phenikaa.cse702051.medbook.config.JwtUtil.JwtClaims;
import com.phenikaa.cse702051.medbook.dto.ChangePasswordRequest;
import com.phenikaa.cse702051.medbook.dto.ForgotPasswordRequest;
import com.phenikaa.cse702051.medbook.dto.LoginRequest;
import com.phenikaa.cse702051.medbook.dto.LoginResponse;
import com.phenikaa.cse702051.medbook.dto.RegisterRequest;
import com.phenikaa.cse702051.medbook.dto.RegisterResponse;
import com.phenikaa.cse702051.medbook.dto.ResetPasswordRequest;
import com.phenikaa.cse702051.medbook.exception.UnauthorizedException;
import com.phenikaa.cse702051.medbook.service.AuthService;
import com.phenikaa.cse702051.medbook.service.PasswordResetService;
import com.phenikaa.cse702051.medbook.service.SessionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Xác thực (YCCN-01…04). {@code register}, {@code login}, {@code forgot-password},
 * {@code reset-password} là công khai; {@code logout} và {@code change-password} yêu cầu đã đăng nhập
 * (quy tắc ở {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final com.phenikaa.cse702051.medbook.service.LoginSessionService loginSessions;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AuthService authService,
            SessionService sessionService,
            com.phenikaa.cse702051.medbook.service.LoginSessionService loginSessions,
            PasswordResetService passwordResetService) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.loginSessions = loginSessions;
        this.passwordResetService = passwordResetService;
    }

    /**
     * YCCN-01: Đăng ký tài khoản Bệnh nhân mới (kèm băm mật khẩu BCrypt cost 12)
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.registerPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * YCCN-02: Đăng nhập, kiểm tra mật khẩu BCrypt, khóa tạm khi sai nhiều lần và cấp JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<com.phenikaa.cse702051.medbook.dto.RefreshResponse> refresh(
            @Valid @RequestBody com.phenikaa.cse702051.medbook.dto.RefreshRequest request) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(loginSessions.refresh(request.refreshToken()));
    }

    /**
     * YCCN-03: Đăng xuất — thu hồi token đang dùng; dùng lại token đó sẽ nhận 401.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        JwtClaims claims = (JwtClaims) request.getAttribute(JwtAuthenticationFilter.CLAIMS_ATTRIBUTE);
        if (claims == null) {
            throw new UnauthorizedException("Yêu cầu chưa được xác thực! Vui lòng cung cấp Bearer Token hợp lệ.");
        }
        sessionService.revoke(claims);
        return ResponseEntity.noContent().build();
    }

    /**
     * YCCN-04: Đổi mật khẩu; mọi phiên cũ (kể cả phiên hiện tại) bị vô hiệu, cần đăng nhập lại.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * YCCN-04: Yêu cầu khôi phục mật khẩu. LUÔN trả 202 như nhau dù email có tồn tại hay không.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "message", "Nếu email đã đăng ký, hướng dẫn đặt lại mật khẩu đã được gửi. Liên kết có hiệu lực trong thời gian ngắn."));
    }

    /**
     * YCCN-04: Đặt lại mật khẩu bằng token một lần dùng trong email.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
