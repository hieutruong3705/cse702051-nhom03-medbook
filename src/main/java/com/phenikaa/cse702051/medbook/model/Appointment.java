package com.phenikaa.cse702051.medbook.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    /**
     * Slot của lịch hẹn. KHÔNG dùng @OneToOne: một slot có thể có nhiều lịch hẹn theo thời gian
     * (lịch đã hủy giữ lại làm lịch sử). Việc "một slot chỉ có một lịch đang hiệu lực" được bảo đảm
     * bằng cột {@link #activeSlotId} có ràng buộc UNIQUE.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private AppointmentSlot slot;

    /**
     * = id của slot khi lịch còn giữ chỗ (BOOKED/IN_PROGRESS/COMPLETED), NULL khi đã hủy. UNIQUE
     * nên CSDL từ chối lịch thứ hai trên cùng một slot đang hiệu lực (lớp bảo vệ thứ hai sau khóa
     * lạc quan {@code @Version} trên slot), còn slot đã hủy đặt lại được.
     */
    @Column(name = "active_slot_id", unique = true)
    private Long activeSlotId;

    /** Dịch vụ khám bệnh nhân chọn khi đặt lịch (FK tới services). */
    @Column(name = "service_id")
    private Long serviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AppointmentStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** Thời điểm đã gửi nhắc lịch (để không gửi trùng); xóa khi đổi/hủy lịch. */
    @Column(name = "reminder_24h_sent_at")
    private LocalDateTime reminder24hSentAt;

    @Column(name = "reminder_2h_sent_at")
    private LocalDateTime reminder2hSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Helper: lấy doctorId từ quan hệ Doctor để tương thích với EncounterService.
     * Lưu ý: vì có getter này, Spring Data sẽ hiểu "doctorId" là thuộc tính; các truy vấn theo bác sĩ
     * trong repository phải viết bằng @Query tường minh ({@code a.doctor.id}).
     */
    public Long getDoctorId() {
        return doctor != null ? doctor.getId() : null;
    }

    /** Đặt lịch vào slot và đồng bộ khóa duy nhất {@link #activeSlotId}. */
    public void assignSlot(AppointmentSlot newSlot) {
        this.slot = newSlot;
        this.doctor = newSlot.getDoctor();
        syncActiveSlot();
    }

    /** Hủy lịch: nhả khóa slot (activeSlotId = null) và ghi lý do/thời điểm. */
    public void cancel(String reason, LocalDateTime when) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = when;
        this.reminder24hSentAt = null;
        this.reminder2hSentAt = null;
        syncActiveSlot();
    }

    private void syncActiveSlot() {
        this.activeSlotId = (status == null || status.holdsSlot()) && slot != null ? slot.getId() : null;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = AppointmentStatus.BOOKED;
        }
        syncActiveSlot();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        syncActiveSlot();
    }
}
