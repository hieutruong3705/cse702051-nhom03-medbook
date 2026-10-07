package com.phenikaa.cse702051.medbook.controller;

import java.net.URI;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.phenikaa.cse702051.medbook.dto.attachment.AttachmentDTO;
import com.phenikaa.cse702051.medbook.service.AttachmentService;
import com.phenikaa.cse702051.medbook.service.AttachmentService.StoredFile;

/**
 * Tệp đính kèm của lần khám (BE-04). Quyền theo vai trò ở {@code SecurityConfig}; quyền theo bản ghi, kiểm tra
 * loại/kích thước và audit ở {@link AttachmentService}.
 */
@RestController
@RequestMapping("/api/v1")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /** Bác sĩ phụ trách tải tệp lên lần khám còn OPEN (multipart, trường {@code file}). */
    @PostMapping(value = "/encounters/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentDTO> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file)
            throws IOException {
        AttachmentDTO created = attachmentService.upload(id, file);
        return ResponseEntity.created(URI.create("/api/v1/attachments/" + created.id() + "/download")).body(created);
    }

    @GetMapping("/encounters/{id}/attachments")
    public ResponseEntity<List<AttachmentDTO>> list(@PathVariable Long id) {
        return ResponseEntity.ok(attachmentService.listByEncounter(id));
    }

    /**
     * Tải tệp. Luôn là {@code attachment} (không hiển thị nội tuyến), {@code nosniff} để trình duyệt không đoán
     * lại loại nội dung, không cache.
     */
    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        StoredFile stored = attachmentService.download(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(stored.attachment().getOriginalFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.attachment().getMimeType()))
                .contentLength(stored.attachment().getFileSize())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(new FileSystemResource(stored.path()));
    }

    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
