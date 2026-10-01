package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.model.Attachment;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.repository.AttachmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;

    private final AttachmentRepository attachmentRepository;
    private final EncounterService encounterService;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            EncounterService encounterService
    ) {
        this.attachmentRepository = attachmentRepository;
        this.encounterService = encounterService;
    }

    // ============================================================
    // UPLOAD
    // ============================================================

    @Transactional
    public Attachment uploadAttachment(
            Long encounterId,
            MultipartFile file
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "File không được để trống"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File không được vượt quá 10 MB"
            );
        }

        // Kiểm tra encounter + quyền truy cập
        Encounter encounter =
                encounterService.getAccessibleById(encounterId);

        if (encounter == null) {
            throw new IllegalArgumentException(
                    "Không tìm thấy encounter với ID: " + encounterId
            );
        }

        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null
                || originalFileName.isBlank()) {
            throw new IllegalArgumentException(
                    "Tên file không hợp lệ"
            );
        }

        // ========================================================
        // KIỂM TRA FILE SIGNATURE
        // ========================================================

        String detectedExtension =
                detectFileExtension(file);

        if (detectedExtension == null) {
            throw new IllegalArgumentException(
                    "File không đúng định dạng PDF/JPG/JPEG/PNG"
            );
        }

        // ========================================================
        // TẠO THƯ MỤC LƯU FILE
        // ========================================================

        Path uploadDirectory =
                Paths.get(
                        System.getProperty("user.dir"),
                        "uploads",
                        "attachments"
                )
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(uploadDirectory);

        // ========================================================
        // TẠO TÊN FILE DO SERVER SINH
        // ========================================================

        String storedFileName =
                UUID.randomUUID()
                        + detectedExtension;

        Path targetPath =
                uploadDirectory
                        .resolve(storedFileName)
                        .normalize();

        // Chống path traversal
        if (!targetPath.startsWith(uploadDirectory)) {
            throw new IllegalArgumentException(
                    "Đường dẫn file không hợp lệ"
            );
        }

        // ========================================================
        // LƯU FILE
        // ========================================================

        try {
            file.transferTo(targetPath.toFile());

            Attachment attachment = new Attachment();

            attachment.setEncounterId(encounterId);
            attachment.setOriginalFileName(
                    originalFileName
            );
            attachment.setStoredFileName(
                    storedFileName
            );
            attachment.setFilePath(
                    targetPath.toString()
            );
            attachment.setMimeType(
                    getMimeType(detectedExtension)
            );
            attachment.setFileSize(
                    file.getSize()
            );
            attachment.setUploadedAt(
                    LocalDateTime.now()
            );

            return attachmentRepository.save(
                    attachment
            );

        } catch (Exception e) {

            // Nếu DB lưu thất bại thì xóa file vật lý
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {
            }

            throw e;
        }
    }

    // ============================================================
    // GET ATTACHMENTS BY ENCOUNTER
    // ============================================================

    @Transactional(readOnly = true)
    public List<Attachment> getAttachmentsByEncounter(
            Long encounterId
    ) {

        // Kiểm tra quyền
        encounterService.getAccessibleById(
                encounterId
        );

        return attachmentRepository
                .findByEncounterId(encounterId);
    }

    // ============================================================
    // GET BY ID
    // ============================================================

    @Transactional(readOnly = true)
    public Attachment getAttachmentById(
            Long id
    ) {

        Attachment attachment =
                attachmentRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Không tìm thấy attachment với ID: "
                                                + id
                                )
                        );

        // Kiểm tra quyền trên encounter
        encounterService.getAccessibleById(
                attachment.getEncounterId()
        );

        return attachment;
    }

    // ============================================================
    // GET FILE PATH
    // ============================================================

    @Transactional(readOnly = true)
    public Path getFilePath(
            Long id
    ) {

        Attachment attachment =
                getAttachmentById(id);

        Path filePath =
                Paths.get(
                        attachment.getFilePath()
                )
                .toAbsolutePath()
                .normalize();

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException(
                    "File vật lý không tồn tại"
            );
        }

        if (!Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException(
                    "Đường dẫn không phải file hợp lệ"
            );
        }

        return filePath;
    }

    // ============================================================
    // DELETE
    // ============================================================

    @Transactional
    public void deleteAttachment(
            Long id
    ) throws IOException {

        Attachment attachment =
                getAttachmentById(id);

        // Chỉ bác sĩ phụ trách được xóa attachment
        encounterService.getAccessibleById(
                attachment.getEncounterId()
        );

        Path filePath =
                Paths.get(
                        attachment.getFilePath()
                )
                .toAbsolutePath()
                .normalize();

        Files.deleteIfExists(filePath);

        attachmentRepository.deleteById(id);
    }

    // ============================================================
    // FILE SIGNATURE
    // ============================================================

    private String detectFileExtension(
            MultipartFile file
    ) throws IOException {

        try (InputStream inputStream =
                     file.getInputStream()) {

            byte[] header = new byte[12];

            int bytesRead =
                    inputStream.read(header);

            if (bytesRead < 4) {
                return null;
            }

            // PDF: %PDF-
            if (bytesRead >= 5
                    && header[0] == 0x25
                    && header[1] == 0x50
                    && header[2] == 0x44
                    && header[3] == 0x46
                    && header[4] == 0x2D) {

                return ".pdf";
            }

            // JPEG: FF D8 FF
            if (bytesRead >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF) {

                return ".jpg";
            }

            // PNG
            if (bytesRead >= 8
                    && (header[0] & 0xFF) == 0x89
                    && (header[1] & 0xFF) == 0x50
                    && (header[2] & 0xFF) == 0x4E
                    && (header[3] & 0xFF) == 0x47
                    && (header[4] & 0xFF) == 0x0D
                    && (header[5] & 0xFF) == 0x0A
                    && (header[6] & 0xFF) == 0x1A
                    && (header[7] & 0xFF) == 0x0A) {

                return ".png";
            }

            return null;
        }
    }

    // ============================================================
    // MIME TYPE
    // ============================================================

    private String getMimeType(
            String extension
    ) {

        return switch (extension.toLowerCase()) {

            case ".pdf" ->
                    "application/pdf";

            case ".jpg", ".jpeg" ->
                    "image/jpeg";

            case ".png" ->
                    "image/png";

            default ->
                    "application/octet-stream";
        };
    }
}