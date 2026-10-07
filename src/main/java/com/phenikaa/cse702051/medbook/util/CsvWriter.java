package com.phenikaa.cse702051.medbook.util;

import java.nio.charset.StandardCharsets;

/**
 * Dựng tệp CSV mở được bằng Excel: UTF-8 kèm BOM, dòng kết thúc bằng CRLF, mọi ô văn bản đặt trong dấu nháy kép
 * (dấu nháy kép bên trong được nhân đôi).
 *
 * <p>Chống chèn công thức (CSV injection): ô văn bản bắt đầu bằng {@code =}, {@code +}, {@code -}, {@code @},
 * tab hoặc ký tự xuống dòng được thêm dấu {@code '} phía trước để bảng tính coi là văn bản, không thực thi. Ô kiểu
 * số ({@link Number}) được ghi nguyên giá trị.
 */
public final class CsvWriter {

    private static final String BOM = "﻿";
    private static final String LINE_END = "\r\n";

    private final StringBuilder out = new StringBuilder(BOM);
    private int rows;

    /** Ghi một dòng; {@code null} thành ô rỗng. */
    public CsvWriter row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells[i]));
        }
        out.append(LINE_END);
        rows++;
        return this;
    }

    /** Số dòng đã ghi, tính cả dòng tiêu đề. */
    public int rowCount() {
        return rows;
    }

    public byte[] toBytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String cell(Object value) {
        if (value == null) {
            return "\"\"";
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        String text = value.toString();
        if (!text.isEmpty() && isFormulaTrigger(text.charAt(0))) {
            text = "'" + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static boolean isFormulaTrigger(char first) {
        return first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r' || first == '\n';
    }
}
