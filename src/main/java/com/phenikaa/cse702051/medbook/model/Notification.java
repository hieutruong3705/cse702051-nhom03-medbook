package com.phenikaa.cse702051.medbook.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thông báo trong ứng dụng gửi cho một người dùng (YCCN-24): đặt, đổi, hủy lịch và nhắc lịch khám. Nội dung chỉ
 * gồm thời gian và tên người liên quan; không bao giờ chứa ghi chú, lý do hủy hay thông tin lâm sàng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_notifications_user_read", columnList = "user_id, read_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Một trong các hằng số của {@code NotificationTypes}. */
    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    /** Lịch hẹn liên quan, để giao diện dẫn tới trang chi tiết. */
    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** {@code null} khi chưa đọc. */
    @Column(name = "read_at")
    private LocalDateTime readAt;
}
