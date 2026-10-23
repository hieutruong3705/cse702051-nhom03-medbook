package com.phenikaa.cse702051.medbook.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.phenikaa.cse702051.medbook.exception.FieldValidationException;

/** Khoảng ngày (tính cả hai đầu) dùng cho báo cáo: điền mặc định khi thiếu và chặn khoảng quá rộng. */
public final class DateRanges {

    private DateRanges() {
    }

    public record DateRange(LocalDate from, LocalDate to) {

        /** Số ngày trong khoảng, tính cả hai đầu. */
        public long days() {
            return ChronoUnit.DAYS.between(from, to) + 1;
        }
    }

    /**
     * Khoảng ngày của một báo cáo. Thiếu cả hai đầu → {@code defaultDays} ngày gần nhất tính đến hôm nay; thiếu
     * một đầu → đầu kia được suy ra để khoảng dài đúng {@code defaultDays} ngày. {@code from} sau {@code to} hoặc
     * khoảng dài hơn {@code maxDays} ngày → 400.
     */
    public static DateRange resolve(LocalDate from, LocalDate to, int defaultDays, int maxDays) {
        LocalDate start = from;
        LocalDate end = to;
        if (start == null && end == null) {
            end = LocalDate.now();
            start = end.minusDays(defaultDays - 1L);
        } else if (start == null) {
            start = end.minusDays(defaultDays - 1L);
        } else if (end == null) {
            end = start.plusDays(defaultDays - 1L);
        }
        requireOrdered(start, end);
        if (ChronoUnit.DAYS.between(start, end) + 1 > maxDays) {
            throw new FieldValidationException("to", "Khoảng thời gian tối đa " + maxDays + " ngày");
        }
        return new DateRange(start, end);
    }

    /** {@code from} không được sau {@code to} (bỏ qua khi thiếu một trong hai) → 400 ở trường {@code to}. */
    public static void requireOrdered(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new FieldValidationException("to", "Ngày kết thúc không được trước ngày bắt đầu");
        }
    }
}
