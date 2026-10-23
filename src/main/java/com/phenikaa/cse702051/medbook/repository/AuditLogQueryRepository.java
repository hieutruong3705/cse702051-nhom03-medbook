package com.phenikaa.cse702051.medbook.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import com.phenikaa.cse702051.medbook.model.AuditLog;

/**
 * Chỉ đọc: tra cứu nhật ký audit cho trang quản trị (lọc động bằng {@code Specification}, luôn phân trang). Tách
 * khỏi {@link AuditLogRepository} để phía ghi audit không bị kéo theo các truy vấn tra cứu.
 */
public interface AuditLogQueryRepository extends Repository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Các mã hành động đã từng được ghi, để giao diện dựng ô chọn bộ lọc. */
    @Query("select distinct a.actionCode from AuditLog a order by a.actionCode")
    List<String> findDistinctActionCodes();
}
