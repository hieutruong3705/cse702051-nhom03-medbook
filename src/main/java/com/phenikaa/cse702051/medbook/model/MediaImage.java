package com.phenikaa.cse702051.medbook.model;

import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ảnh hiển thị công khai (ảnh đại diện bác sĩ, ảnh chuyên khoa), lưu thẳng trong CSDL để triển khai lại container
 * không làm mất ảnh. Mỗi lần tải ảnh mới tạo một bản ghi mới (id mới) nên đường dẫn ảnh đổi theo và trình duyệt được
 * phép lưu đệm lâu. Bác sĩ và chuyên khoa chỉ giữ {@code id} ảnh, không liên kết thực thể, để truy vấn danh sách
 * không bao giờ tải phần dữ liệu ảnh.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "media_images")
public class MediaImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
