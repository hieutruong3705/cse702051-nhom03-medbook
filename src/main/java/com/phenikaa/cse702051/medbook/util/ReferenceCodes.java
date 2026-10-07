package com.phenikaa.cse702051.medbook.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;

import com.phenikaa.cse702051.medbook.exception.ConflictException;

/**
 * Mã tham chiếu cho chứng từ (đơn thuốc, hóa đơn) dạng {@code TIỀN_TỐ-yyyyMMdd-XXXXXX}, trong đó {@code XXXXXX}
 * là 6 ký tự chữ hoa và số ngẫu nhiên. Mã không lộ số thứ tự hay số lượng chứng từ trong ngày.
 */
public final class ReferenceCodes {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int RANDOM_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ReferenceCodes() {
    }

    /**
     * Sinh một mã chưa được dùng. Gặp mã đã tồn tại thì sinh lại, tối đa 5 lần; hết lượt → 409. Ràng buộc UNIQUE
     * ở CSDL vẫn là lớp chặn cuối cho hai yêu cầu đồng thời sinh cùng một mã.
     *
     * @param alreadyUsed kiểm tra mã đã có trong CSDL hay chưa
     */
    public static String next(String prefix, LocalDate date, Predicate<String> alreadyUsed) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(prefix).append('-').append(DATE.format(date)).append('-');
            for (int i = 0; i < RANDOM_LENGTH; i++) {
                code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            if (!alreadyUsed.test(code.toString())) {
                return code.toString();
            }
        }
        throw new ConflictException("Không tạo được mã chứng từ. Vui lòng thử lại!");
    }
}
