package com.phenikaa.cse702051.medbook.util;

import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.phenikaa.cse702051.medbook.exception.FieldValidationException;

/**
 * Quy tắc phân trang chung của mọi API danh sách: {@code page} âm coi như 0, {@code size} mặc định 20 và bị cắt
 * về tối đa 100, {@code sort} chỉ nhận cột trong danh sách trắng của từng API (cột lạ → 400).
 */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** Tên thuộc tính khóa chính của mọi entity có danh sách phân trang. */
    private static final String ID = "id";

    private PageRequests() {
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), clampSize(size), sort);
    }

    public static int clampSize(int size) {
        return size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    /**
     * Đọc tham số {@code sort} dạng {@code cột} hoặc {@code cột,asc|desc}. Tham số rỗng dùng {@code fallback}.
     * Kết quả luôn kèm khóa chính {@code id} làm tiêu chí phụ ({@code fallback} phải tự có sẵn).
     *
     * @param allowed các thuộc tính được phép sắp xếp (tên thuộc tính của entity)
     */
    public static Sort parseSort(String sort, Set<String> allowed, Sort fallback) {
        if (sort == null || sort.isBlank()) {
            return fallback;
        }
        String[] parts = sort.trim().split(",");
        String property = parts[0].trim();
        if (!allowed.contains(property)) {
            throw new FieldValidationException("sort", "Chỉ sắp xếp được theo: " + String.join(", ", allowed));
        }
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new FieldValidationException("sort", "Chiều sắp xếp phải là asc hoặc desc");
        }
        Sort.Direction order = "desc".equals(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort primary = Sort.by(order, property);
        // Khóa chính làm tiêu chí phụ: các dòng trùng giá trị sắp xếp không bị lặp hoặc bỏ sót giữa các trang.
        return ID.equals(property) ? primary : primary.and(Sort.by(order, ID));
    }
}
