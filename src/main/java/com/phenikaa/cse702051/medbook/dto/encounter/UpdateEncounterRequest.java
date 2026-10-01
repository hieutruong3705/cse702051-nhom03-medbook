package com.phenikaa.cse702051.medbook.dto.encounter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cập nhật lần khám (chỉ khi còn OPEN). Trường {@code null} = giữ nguyên; chuỗi rỗng = xóa nội dung.
 * {@code status = COMPLETED} hoàn thành khám và khóa nội dung; {@code OPEN} là không đổi.
 */
public record UpdateEncounterRequest(
        @Size(max = 5000, message = "Lý do khám tối đa 5000 ký tự")
        String chiefComplaint,

        @Size(max = 5000, message = "Chẩn đoán tối đa 5000 ký tự")
        String diagnosis,

        @Size(max = 10000, message = "Ghi chú lâm sàng tối đa 10000 ký tự")
        String clinicalNotes,

        @Size(max = 5000, message = "Kế hoạch điều trị tối đa 5000 ký tự")
        String treatmentPlan,

        @Size(max = 2000, message = "Ghi chú tái khám tối đa 2000 ký tự")
        String followUpNote,

        @Pattern(regexp = "(?i)^(OPEN|COMPLETED)$", message = "Trạng thái phải là OPEN hoặc COMPLETED")
        String status,

        @Valid
        ClinicalSummary clinicalSummary
) {

    /** Tóm tắt bệnh án cập nhật từ lần khám (ghi qua {@code MedicalRecordService.updateClinicalSummary}). */
    public record ClinicalSummary(
            @Pattern(regexp = "(?i)^(A|B|AB|O)[+-]?$", message = "Nhóm máu không hợp lệ (A, B, AB, O, có thể kèm + hoặc -)")
            String bloodType,

            @Size(max = 2000, message = "Bệnh mạn tính tối đa 2000 ký tự")
            String chronicConditions,

            @Size(max = 2000, message = "Thông tin dị ứng tối đa 2000 ký tự")
            String allergyNotes,

            @Size(max = 5000, message = "Tiền sử bệnh tối đa 5000 ký tự")
            String medicalHistory,

            @Size(max = 2000, message = "Thuốc đang dùng tối đa 2000 ký tự")
            String currentMedications
    ) {
    }
}
