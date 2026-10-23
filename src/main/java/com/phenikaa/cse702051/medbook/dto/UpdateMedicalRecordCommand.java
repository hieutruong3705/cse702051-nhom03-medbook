package com.phenikaa.cse702051.medbook.dto;

/**
 * Lệnh cập nhật tóm tắt bệnh án do luồng khám (Dev 4) gọi qua
 * {@code MedicalRecordService.updateClinicalSummary}. Trường null = giữ nguyên.
 * Không có {@code patient}, {@code recordCode}, {@code status}.
 */
public record UpdateMedicalRecordCommand(
        String bloodType,
        String chronicConditions,
        String allergyNotes,
        String medicalHistory,
        String currentMedications
) {
}
