package com.phenikaa.cse702051.medbook.dto.attachment;

import java.time.LocalDateTime;

/**
 * Siêu dữ liệu tệp đính kèm. Cố ý KHÔNG có đường dẫn lưu trữ hay tên tệp do server sinh:
 * client chỉ tải tệp qua {@code GET /attachments/{id}/download}.
 */
public record AttachmentDTO(
        Long id,
        Long encounterId,
        String originalFileName,
        String mimeType,
        Long fileSize,
        LocalDateTime uploadedAt
) {
}
