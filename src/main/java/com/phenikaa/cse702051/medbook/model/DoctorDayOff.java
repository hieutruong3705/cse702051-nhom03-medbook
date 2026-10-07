package com.phenikaa.cse702051.medbook.model;

import java.time.LocalDate;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ngày nghỉ của bác sĩ: cả ngày đó không có giờ trống nào, kể cả khi đã có ca làm việc. Mỗi bác sĩ chỉ có một bản
 * ghi cho một ngày (UNIQUE {@code doctor_id}, {@code off_date}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "doctor_day_offs", uniqueConstraints = @UniqueConstraint(
        name = "uk_doctor_day_offs_doctor_date", columnNames = { "doctor_id", "off_date" }))
public class DoctorDayOff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "off_date", nullable = false)
    private LocalDate offDate;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
