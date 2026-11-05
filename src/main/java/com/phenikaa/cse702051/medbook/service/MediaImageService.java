package com.phenikaa.cse702051.medbook.service;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.phenikaa.cse702051.medbook.exception.ApiException;
import com.phenikaa.cse702051.medbook.exception.BadRequestException;
import com.phenikaa.cse702051.medbook.exception.ErrorCode;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.MediaImage;
import com.phenikaa.cse702051.medbook.repository.MediaImageRepository;

/**
 * Lưu và đọc ảnh công khai (ảnh đại diện bác sĩ, ảnh chuyên khoa).
 *
 * <p>Chỉ nhận JPEG, PNG, WebP, tối đa {@link #MAX_BYTES}. Loại ảnh xác định bằng chữ ký đầu tệp (magic bytes), không
 * tin tên tệp hay Content-Type trình duyệt gửi lên, nên tệp HTML/SVG/script đổi đuôi thành .png bị từ chối. SVG bị
 * loại hẳn vì có thể chứa script.
 */
@Service
public class MediaImageService {

    public static final int MAX_BYTES = 1024 * 1024;
    private static final String URL_PREFIX = "/api/v1/images/";

    private final MediaImageRepository repository;

    public MediaImageService(MediaImageRepository repository) {
        this.repository = repository;
    }

    /** Đường dẫn công khai của ảnh, {@code null} khi chưa có ảnh. Dùng trong các DTO. */
    public static String urlOf(Long imageId) {
        return imageId == null ? null : URL_PREFIX + imageId;
    }

    @Transactional
    public MediaImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Chưa chọn ảnh!");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "Ảnh vượt quá dung lượng cho phép (tối đa 1 MB)!");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Không đọc được ảnh gửi lên!");
        }
        String contentType = detectType(data);
        if (contentType == null) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Chỉ nhận ảnh JPEG, PNG hoặc WebP!");
        }
        return repository.save(MediaImage.builder().contentType(contentType).sizeBytes(data.length).data(data).build());
    }

    @Transactional(readOnly = true)
    public MediaImage load(Long id) {
        MediaImage image = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh!"));
        image.getData(); // nạp phần dữ liệu (LAZY) trong giao dịch
        return image;
    }

    /** Xóa ảnh cũ sau khi đã gỡ liên kết; bỏ qua khi {@code id} rỗng. */
    @Transactional
    public void deleteIfPresent(Long id) {
        if (id != null) {
            repository.deleteById(id);
        }
    }

    /** Nhận dạng loại ảnh từ chữ ký đầu tệp; trả {@code null} nếu không phải JPEG, PNG hay WebP. */
    static String detectType(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
