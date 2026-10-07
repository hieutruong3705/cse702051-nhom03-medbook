package com.phenikaa.cse702051.medbook.service;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Xóa vùng đệm danh mục công khai (xem {@code CacheConfig}) đúng lúc: sau khi giao dịch thay đổi danh mục đã
 * commit, để một yêu cầu đọc chen vào giữa không nạp lại dữ liệu cũ vào vùng đệm.
 */
@Component
public class PublicCatalogCache {

    private final CacheManager cacheManager;

    public PublicCatalogCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Xóa một vùng đệm khi giao dịch hiện tại commit (không làm gì nếu giao dịch bị hoàn tác). Gọi ngoài giao dịch
     * thì xóa ngay.
     */
    public void evictAfterCommit(String cacheName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict(cacheName);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict(cacheName);
            }
        });
    }

    /** Làm mới định kỳ: giới hạn thời gian dữ liệu cũ còn hiện ra khi CSDL bị sửa ngoài ứng dụng. */
    @Scheduled(fixedDelayString = "${medbook.cache.ttl-ms:300000}",
            initialDelayString = "${medbook.cache.ttl-ms:300000}")
    public void evictAll() {
        cacheManager.getCacheNames().forEach(this::evict);
    }

    private void evict(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
