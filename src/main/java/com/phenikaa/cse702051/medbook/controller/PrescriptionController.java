package com.phenikaa.cse702051.medbook.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.prescription.PrescriptionDTO;
import com.phenikaa.cse702051.medbook.dto.prescription.PrescriptionRequest;
import com.phenikaa.cse702051.medbook.service.PrescriptionService;

/**
 * Đơn thuốc của lần khám (YCCN-18). Lần khám lấy từ đường dẫn; quyền theo bản ghi (bác sĩ phụ trách ghi, bệnh
 * nhân chủ và bác sĩ phụ trách đọc) và khóa sau khi hoàn thành khám nằm ở {@link PrescriptionService}.
 */
@RestController
@RequestMapping("/api/v1")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    /** Bác sĩ phụ trách kê một đơn mới (chưa có dòng thuốc) cho lần khám đang mở. */
    @PostMapping("/encounters/{encounterId}/prescriptions")
    public PrescriptionDTO create(@PathVariable Long encounterId, @RequestBody PrescriptionRequest request) {
        return prescriptionService.create(encounterId, request);
    }

    /** Các đơn thuốc của lần khám, kèm dòng thuốc. */
    @GetMapping("/encounters/{encounterId}/prescriptions")
    public List<PrescriptionDTO> listByEncounter(@PathVariable Long encounterId) {
        return prescriptionService.listByEncounter(encounterId);
    }

    @GetMapping("/prescriptions/{id}")
    public PrescriptionDTO get(@PathVariable Long id) {
        return prescriptionService.get(id);
    }

    /** Sửa ghi chú hoặc trạng thái của đơn khi lần khám còn mở. */
    @PutMapping("/prescriptions/{id}")
    public PrescriptionDTO update(@PathVariable Long id, @RequestBody PrescriptionRequest request) {
        return prescriptionService.update(id, request);
    }
}
