package com.phenikaa.cse702051.medbook.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(unique = true, length = 190)
    private String email;

    @Column(length = 30)
    private String phone;

    /** ACTIVE hoặc LOCKED (Admin khóa). Khóa tự động do đăng nhập sai dùng {@link #lockedUntil}. */
    @Column(nullable = false, length = 20)
    private String status;

    /** Số lần đăng nhập sai liên tiếp kể từ lần thành công/khóa gần nhất. */
    @ColumnDefault("0")
    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private int failedLoginCount = 0;

    /** Bị khóa tự động do đăng nhập sai quá số lần cho phép, đến thời điểm này. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * Phiên bản token. Mỗi JWT mang theo giá trị này; tăng lên khi khóa tài khoản, đổi quyền,
     * đổi/đặt lại mật khẩu để vô hiệu hóa mọi token đã cấp trước đó.
     */
    @ColumnDefault("0")
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
