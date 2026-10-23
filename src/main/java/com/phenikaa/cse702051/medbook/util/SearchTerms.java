package com.phenikaa.cse702051.medbook.util;

import java.util.Locale;

/**
 * Chuyển từ khóa người dùng gõ thành mẫu {@code LIKE} an toàn: so khớp "chứa", không phân biệt hoa thường, và các
 * ký tự {@code %}, {@code _} do người dùng gõ được coi là ký tự thường (không thành ký tự đại diện, nên gõ
 * {@code %} không khớp mọi bản ghi).
 *
 * <p>Truy vấn dùng mẫu này phải so sánh trên {@code lower(cột)} và khai báo ký tự thoát {@link #ESCAPE}:
 * JPQL {@code lower(x.name) like :pattern escape '!'}; Criteria {@code cb.like(cb.lower(path), pattern, ESCAPE)}.
 * Dùng {@code !} thay cho {@code \} vì dấu gạch chéo ngược được MySQL và H2 xử lý khác nhau trong chuỗi.
 */
public final class SearchTerms {

    public static final char ESCAPE = '!';

    private SearchTerms() {
    }

    /** Mẫu {@code %từ khóa%} đã thoát ký tự đại diện; từ khóa rỗng cho {@code %%} (khớp mọi giá trị khác null). */
    public static String toLikePattern(String keyword) {
        String cleaned = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        StringBuilder pattern = new StringBuilder(cleaned.length() + 2).append('%');
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == ESCAPE || c == '%' || c == '_') {
                pattern.append(ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }

    /** {@code true} khi không có từ khóa (null hoặc chỉ khoảng trắng): người gọi có thể bỏ hẳn điều kiện tìm kiếm. */
    public static boolean isBlank(String keyword) {
        return keyword == null || keyword.isBlank();
    }
}
