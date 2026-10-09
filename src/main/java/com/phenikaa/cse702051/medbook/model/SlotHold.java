package com.phenikaa.cse702051.medbook.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bản giữ chỗ có thời hạn trên một khung giờ: người dùng đã chọn khung giờ nhưng chưa xác nhận đặt lịch. Mỗi khung
 * giờ có tối đa một dòng (UNIQUE {@code slot_id}). Dòng đã qua {@code expires_at} được coi là không tồn tại ở mọi
 * nơi đọc; tác vụ dọn chỉ xóa cho gọn bảng.
 *
 * <p>Khung giờ và người giữ lưu bằng ID thuần (không ánh xạ quan hệ): khóa ngoại và {@code ON DELETE CASCADE} do
 * migration tạo, để việc gỡ một khung giờ không bị một bản giữ chỗ còn sót chặn lại.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "slot_holds",
        uniqueConstraints = @UniqueConstraint(name = "uk_slot_holds_slot", columnNames = "slot_id"),
        indexes = {
                @Index(name = "idx_slot_holds_expires", columnList = "expires_at"),
                @Index(name = "idx_slot_holds_user", columnList = "user_id")
        })
public class SlotHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    /** Tài khoản đang giữ: bệnh nhân tự đặt, hoặc lễ tân đang đặt hộ tại quầy. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
