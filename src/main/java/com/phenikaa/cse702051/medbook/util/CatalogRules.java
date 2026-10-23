package com.phenikaa.cse702051.medbook.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.phenikaa.cse702051.medbook.exception.FieldValidationException;

import jakarta.persistence.criteria.Predicate;

/**
 * Quy tắc chung của ba danh mục (chuyên khoa, dịch vụ khám, thuốc): mã được chuẩn hóa (cắt khoảng trắng, viết hoa)
 * và bất biến sau khi tạo; trạng thái chỉ là {@code ACTIVE} hoặc {@code INACTIVE}.
 */
public final class CatalogRules {

    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";

    private CatalogRules() {
    }

    /** Mã bắt buộc khi tạo: cắt khoảng trắng và viết hoa. */
    public static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new FieldValidationException("code", "Mã không được để trống");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /** Khi sửa: không gửi mã thì giữ nguyên; gửi mã khác mã hiện có → 400 ở trường {@code code}. */
    public static void assertCodeUnchanged(String requested, String existing) {
        if (requested == null || requested.isBlank()) {
            return;
        }
        if (!requested.trim().equalsIgnoreCase(existing)) {
            throw new FieldValidationException("code", "Không thể đổi mã sau khi đã tạo");
        }
    }

    /** Trạng thái khi tạo/sửa: bỏ trống dùng {@code fallback}. */
    public static String statusOrDefault(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        return parseStatus(status);
    }

    /** Bộ lọc trạng thái của danh sách: bỏ trống nghĩa là không lọc ({@code null}). */
    public static String statusFilter(String status) {
        return status == null || status.isBlank() ? null : parseStatus(status);
    }

    public static boolean isActive(String status) {
        return ACTIVE.equalsIgnoreCase(status);
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Điều kiện lọc danh sách danh mục: theo trạng thái (nếu có) và từ khóa khớp một trong các cột văn bản. Từ khóa
     * luôn đi qua {@link SearchTerms#toLikePattern} nên ký tự đại diện do người dùng gõ không có tác dụng.
     */
    public static <T> Specification<T> matching(String status, String keyword, String... textFields) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (status != null) {
                all.add(cb.equal(root.get("status"), status));
            }
            if (!SearchTerms.isBlank(keyword)) {
                String pattern = SearchTerms.toLikePattern(keyword);
                List<Predicate> any = new ArrayList<>();
                for (String field : textFields) {
                    any.add(cb.like(cb.lower(root.get(field)), pattern, SearchTerms.ESCAPE));
                }
                all.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private static String parseStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE.equals(normalized) && !INACTIVE.equals(normalized)) {
            throw new FieldValidationException("status", "Trạng thái phải là ACTIVE hoặc INACTIVE");
        }
        return normalized;
    }
}
