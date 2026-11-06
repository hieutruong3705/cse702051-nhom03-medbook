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
@Table(name = "doctors")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Liên kết với tài khoản User do Dev 1 quản lý
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // Liên kết với chuyên khoa Specialty do Dev 2 quản lý
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialty_id")
    private Specialty specialty;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    /** Số giấy phép hành nghề; mỗi số chỉ thuộc về một hồ sơ bác sĩ. */
    @Column(name = "license_number", length = 50, unique = true)
    private String licenseNumber;

    @Column(columnDefinition = "TEXT")
    private String bio;

    // Thông tin giới thiệu bác sĩ tự cập nhật, hiện trên trang công khai (V5)
    @Column(name = "academic_title", length = 100)
    private String academicTitle;

    @Column(name = "years_of_experience")
    private Short yearsOfExperience;

    @Column(columnDefinition = "TEXT")
    private String education;

    @Column(columnDefinition = "TEXT")
    private String expertise;

    @Column(name = "work_history", columnDefinition = "TEXT")
    private String workHistory;

    /** Ảnh đại diện ({@code media_images.id}); {@code null} thì giao diện vẽ ảnh minh họa SVG. */
    @Column(name = "avatar_image_id")
    private Long avatarImageId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}