package com.phenikaa.cse702051.medbook.dto.report;

/** Tệp CSV đã dựng xong (UTF-8 kèm BOM) cùng tên tệp gợi ý khi tải về. */
public record CsvExport(
        String fileName,
        byte[] content
) {
}
