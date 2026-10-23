package com.phenikaa.cse702051.medbook.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.notification.NotificationDTO;
import com.phenikaa.cse702051.medbook.dto.notification.UnreadCountDTO;
import com.phenikaa.cse702051.medbook.service.NotificationService;

/**
 * Thông báo trong ứng dụng của người đang đăng nhập (YCCN-24). Chỉ bệnh nhân và bác sĩ (xem
 * {@code SecurityConfig}); người nhận luôn lấy từ JWT nên không có tham số {@code userId} nào.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Thông báo của tôi, mới nhất trước. {@code unreadOnly=true} chỉ lấy thông báo chưa đọc. */
    @GetMapping("/me")
    public PageResponse<NotificationDTO> mine(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.listMine(unreadOnly, page, size);
    }

    @GetMapping("/me/unread-count")
    public UnreadCountDTO unreadCount() {
        return notificationService.countUnread();
    }

    /** Đánh dấu một thông báo đã đọc. Thông báo của người khác → 404. */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.noContent().build();
    }
}
