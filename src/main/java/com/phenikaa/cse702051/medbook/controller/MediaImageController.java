package com.phenikaa.cse702051.medbook.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.model.MediaImage;
import com.phenikaa.cse702051.medbook.service.MediaImageService;

/**
 * Trả ảnh công khai theo id (ảnh đại diện bác sĩ, ảnh chuyên khoa). Ảnh đổi thì id đổi, nên được phép lưu đệm một
 * năm. {@code nosniff} và CSP {@code sandbox} để trình duyệt không bao giờ chạy nội dung này như trang web.
 */
@RestController
@RequestMapping("/api/v1/images")
public class MediaImageController {

    private final MediaImageService mediaImageService;

    public MediaImageController(MediaImageService mediaImageService) {
        this.mediaImageService = mediaImageService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(@PathVariable Long id) {
        MediaImage image = mediaImageService.load(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(image.getData());
    }
}
