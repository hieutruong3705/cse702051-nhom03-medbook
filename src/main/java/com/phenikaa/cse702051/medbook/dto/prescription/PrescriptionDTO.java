package com.phenikaa.cse702051.medbook.dto.prescription;

import java.time.LocalDateTime;
import java.util.List;

import com.phenikaa.cse702051.medbook.model.Prescription;

/** Đơn thuốc của một lần khám kèm các dòng thuốc theo thứ tự kê. */
public record PrescriptionDTO(
        Long id,
        String prescriptionCode,
        Long encounterId,
        LocalDateTime issuedAt,
        String notes,
        String status,
        List<PrescriptionItemDTO> items
) {
    public static PrescriptionDTO from(Prescription prescription, List<PrescriptionItemDTO> items) {
        return new PrescriptionDTO(
                prescription.getId(),
                prescription.getPrescriptionCode(),
                prescription.getEncounterId(),
                prescription.getIssuedAt(),
                prescription.getNotes(),
                prescription.getStatus(),
                items);
    }
}
