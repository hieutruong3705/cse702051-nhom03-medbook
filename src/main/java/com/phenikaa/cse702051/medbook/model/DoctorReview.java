package com.phenikaa.cse702051.medbook.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Đánh giá của bệnh nhân cho một lần khám đã hoàn thành (V5). Mỗi lịch hẹn có tối đa một đánh giá
 * ({@code UNIQUE(appointment_id)}); bác sĩ được đánh giá là bác sĩ của lịch hẹn đó, không do người gửi chọn.
 * {@code status}: {@link #VISIBLE} hiện trên trang công khai, {@link #HIDDEN} bị quản trị viên ẩn kèm lý do.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "doctor_reviews")
public class DoctorReview {

    public static final String VISIBLE = "VISIBLE";
    public static final String HIDDEN = "HIDDEN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false, unique = true)
    private Long appointmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer rating;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = VISIBLE;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @Column(name = "hidden_by")
    private Long hiddenBy;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isVisible() {
        return VISIBLE.equals(status);
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = VISIBLE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
