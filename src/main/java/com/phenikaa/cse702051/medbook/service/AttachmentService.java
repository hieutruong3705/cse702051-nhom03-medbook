package com.phenikaa.cse702051.medbook.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.phenikaa.cse702051.medbook.dto.attachment.AttachmentDTO;
import com.phenikaa.cse702051.medbook.exception.ApiException;
import com.phenikaa.cse702051.medbook.exception.BadRequestException;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.ErrorCode;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Attachment;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.repository.AttachmentRepository;

/**
 * Tệp đính kèm của lần khám (kết quả xét nghiệm, ảnh). Quy tắc an toàn:
 *
 * <ul>
 * <li>Tải lên/xóa: chỉ bác sĩ phụ trách và chỉ khi lần khám còn OPEN. Đọc/tải xuống: bệnh nhân chủ hoặc bác sĩ
 * phụ trách; Admin bị 403. Tải xuống và xóa ghi audit.</li>
 * <li>Loại tệp xác định bằng chữ ký nhị phân (PDF/JPEG/PNG), không tin tên hay Content-Type do client gửi; đuôi
 * tên tệp phải khớp loại thật. Sai loại → 415, quá 10 MB → 413.</li>
 * <li>Tên lưu trữ do server sinh (UUID + đuôi theo loại thật); tên gốc được làm sạch (bỏ đường dẫn, ký tự điều
 * khiển) và chỉ dùng để hiển thị. Đường dẫn chỉ nằm trong thư mục {@code medbook.upload-dir}; không bao giờ
 * trả ra ngoài ({@link AttachmentDTO} không có đường dẫn).</li>
 * </ul>
 */
@Service
public class AttachmentService {

    public static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final String SUBDIRECTORY = "attachments";
    private static final int MAX_NAME_LENGTH = 200;

    private final AttachmentRepository attachmentRepository;
    private final EncounterService encounterService;
    private final EncounterAccessPolicy policy;
    private final AuditLogService auditLogService;
    private final Path storageRoot;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            EncounterService encounterService,
            EncounterAccessPolicy policy,
            AuditLogService auditLogService,
            @Value("${medbook.upload-dir:./uploads}") String uploadDir) {
        this.attachmentRepository = attachmentRepository;
        this.encounterService = encounterService;
        this.policy = policy;
        this.auditLogService = auditLogService;
        this.storageRoot = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(SUBDIRECTORY);
    }

    /** Tệp đã kiểm quyền, sẵn sàng stream về client. */
    public record StoredFile(Attachment attachment, Path path) {
    }

    // ================= Tải lên =================

    @Transactional
    public AttachmentDTO upload(Long encounterId, MultipartFile file) throws IOException {
        Encounter encounter = encounterService.getById(encounterId);
        policy.assertDoctorOwns(encounter);
        assertOpen(encounter);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp không được để trống!");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "Tệp vượt quá dung lượng cho phép (tối đa 10 MB)!");
        }

        FileType type = detectType(file);
        String displayName = sanitizeFileName(file.getOriginalFilename());
        if (type == null || !type.acceptsExtension(extensionOf(displayName))) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Chỉ chấp nhận tệp PDF, JPG hoặc PNG đúng định dạng!");
        }

        Files.createDirectories(storageRoot);
        String storedName = UUID.randomUUID() + type.extension;
        Path target = storageRoot.resolve(storedName).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BadRequestException("Đường dẫn tệp không hợp lệ!");
        }

        try {
            file.transferTo(target);

            Attachment attachment = new Attachment();
            attachment.setEncounterId(encounterId);
            attachment.setOriginalFileName(displayName);
            attachment.setStoredFileName(storedName);
            attachment.setFilePath(SUBDIRECTORY + "/" + storedName);
            attachment.setMimeType(type.mimeType);
            attachment.setFileSize(file.getSize());
            attachment.setUploadedAt(LocalDateTime.now());
            attachment = attachmentRepository.saveAndFlush(attachment);

            // Giao dịch rollback (kể cả lỗi ghi audit) thì tệp vật lý không được mồ côi
            deleteFileOnRollback(target);

            auditLogService.record(AuditEvent.of(AuditActions.ATTACHMENT_UPLOAD, AuditActions.ENTITY_ATTACHMENTS,
                    attachment.getId()).with("encounterId", encounterId).with("fileSize", file.getSize()));
            return toDTO(attachment);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // tệp mồ côi còn lại sẽ không được tham chiếu bởi bản ghi nào
            }
            throw e;
        }
    }

    // ================= Đọc =================

    @Transactional(readOnly = true)
    public List<AttachmentDTO> listByEncounter(Long encounterId) {
        encounterService.getAccessibleById(encounterId);
        return attachmentRepository.findByEncounterId(encounterId).stream().map(AttachmentService::toDTO).toList();
    }

    /** Kiểm quyền, ghi audit {@code ATTACHMENT_DOWNLOAD} và trả tệp để stream. */
    @Transactional
    public StoredFile download(Long id) {
        Attachment attachment = requireAttachment(id);
        encounterService.getAccessibleById(attachment.getEncounterId());

        Path path = resolveStoredPath(attachment);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Tệp không còn tồn tại trên máy chủ!");
        }
        auditLogService.record(AuditEvent.of(AuditActions.ATTACHMENT_DOWNLOAD, AuditActions.ENTITY_ATTACHMENTS, id)
                .with("encounterId", attachment.getEncounterId()));
        return new StoredFile(attachment, path);
    }

    // ================= Xóa =================

    @Transactional
    public void delete(Long id) {
        Attachment attachment = requireAttachment(id);
        Encounter encounter = encounterService.getById(attachment.getEncounterId());
        policy.assertDoctorOwns(encounter);
        assertOpen(encounter);

        Path path = resolveStoredPath(attachment);
        attachmentRepository.delete(attachment);
        attachmentRepository.flush();
        auditLogService.record(AuditEvent.of(AuditActions.ATTACHMENT_DELETE, AuditActions.ENTITY_ATTACHMENTS, id)
                .with("encounterId", attachment.getEncounterId()));

        // Chỉ xóa tệp vật lý sau khi giao dịch xóa bản ghi commit thành công
        deleteFileAfterCommit(path);
    }

    // ================= Chi tiết =================

    private Attachment requireAttachment(Long id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tệp đính kèm với ID: " + id));
    }

    private static void assertOpen(Encounter encounter) {
        if (!EncounterService.OPEN.equals(encounter.getStatus())) {
            throw new ConflictException("Lần khám đã hoàn thành, không thể thay đổi tệp đính kèm!");
        }
    }

    /** Ghép đường dẫn lưu trữ và bảo đảm nó nằm trong thư mục upload (chống path traversal từ dữ liệu DB). */
    private Path resolveStoredPath(Attachment attachment) {
        Path path;
        try {
            path = storageRoot.getParent().resolve(attachment.getFilePath()).normalize();
        } catch (InvalidPathException e) {
            path = null;
        }
        if (path == null || !path.startsWith(storageRoot)) {
            log.error("Đường dẫn tệp đính kèm {} không hợp lệ hoặc nằm ngoài thư mục lưu trữ", attachment.getId());
            throw new ResourceNotFoundException("Tệp không còn tồn tại trên máy chủ!");
        }
        return path;
    }

    private static void deleteFileOnRollback(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    quietDelete(path);
                }
            }
        });
    }

    private static void deleteFileAfterCommit(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            quietDelete(path);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                quietDelete(path);
            }
        });
    }

    private static void quietDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Không xóa được tệp vật lý {}: {}", path.getFileName(), e.getMessage());
        }
    }

    private static AttachmentDTO toDTO(Attachment attachment) {
        return new AttachmentDTO(attachment.getId(), attachment.getEncounterId(), attachment.getOriginalFileName(),
                attachment.getMimeType(), attachment.getFileSize(), attachment.getUploadedAt());
    }

    /** Bỏ thành phần đường dẫn, ký tự điều khiển và ký tự nguy hiểm; cắt ngắn giữ đuôi tệp. */
    static String sanitizeFileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>:|?*]", "_").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new BadRequestException("Tên tệp không hợp lệ!");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            String extension = extensionOf(name);
            String suffix = extension.isEmpty() ? "" : "." + extension;
            name = name.substring(0, MAX_NAME_LENGTH - suffix.length()) + suffix;
        }
        return name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // ================= Nhận diện loại tệp theo chữ ký =================

    private enum FileType {
        PDF(".pdf", "application/pdf", Set.of("pdf")),
        JPEG(".jpg", "image/jpeg", Set.of("jpg", "jpeg")),
        PNG(".png", "image/png", Set.of("png"));

        final String extension;
        final String mimeType;
        private final Set<String> acceptedExtensions;

        FileType(String extension, String mimeType, Set<String> acceptedExtensions) {
            this.extension = extension;
            this.mimeType = mimeType;
            this.acceptedExtensions = acceptedExtensions;
        }

        boolean acceptsExtension(String fileExtension) {
            return acceptedExtensions.contains(fileExtension);
        }
    }

    private static FileType detectType(MultipartFile file) throws IOException {
        byte[] header = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        }
        if (read >= 5 && header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46
                && header[4] == 0x2D) {
            return FileType.PDF;
        }
        if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return FileType.JPEG;
        }
        if (read >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return FileType.PNG;
        }
        return null;
    }
}
