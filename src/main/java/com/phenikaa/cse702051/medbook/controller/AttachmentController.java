package com.phenikaa.cse702051.medbook.controller;

import com.phenikaa.cse702051.medbook.model.Attachment;
import com.phenikaa.cse702051.medbook.service.AttachmentService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * Upload attachment cho encounter.
     *
     * POST /api/encounters/{id}/attachments
     */
    @PostMapping(
            value = "/encounters/{id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Attachment> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) throws Exception {

        Attachment attachment =
                attachmentService.uploadAttachment(id, file);

        return ResponseEntity.ok(attachment);
    }

    /**
     * Lấy danh sách attachment của encounter.
     *
     * GET /api/encounters/{id}/attachments
     */
    @GetMapping("/encounters/{id}/attachments")
    public ResponseEntity<List<Attachment>> getAttachments(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                attachmentService.getAttachmentsByEncounter(id)
        );
    }

    /**
     * Download attachment.
     *
     * GET /api/attachments/{id}/download
     */
    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long id
    ) throws Exception {

        Attachment attachment =
                attachmentService.getAttachmentById(id);

        Path filePath =
                attachmentService.getFilePath(id);

        Resource resource =
                new FileSystemResource(filePath);

        String contentType = attachment.getMimeType();

        MediaType mediaType;

        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition contentDisposition =
                ContentDisposition.attachment()
                        .filename(attachment.getOriginalFileName())
                        .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition.toString()
                )
                .body(resource);
    }

    /**
     * Xóa attachment.
     *
     * DELETE /api/attachments/{id}
     */
    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long id
    ) throws Exception {

        attachmentService.deleteAttachment(id);

        return ResponseEntity.noContent().build();
    }
}