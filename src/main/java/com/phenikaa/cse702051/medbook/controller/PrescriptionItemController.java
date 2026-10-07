package com.phenikaa.cse702051.medbook.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.prescription.PrescriptionItemDTO;
import com.phenikaa.cse702051.medbook.dto.prescription.PrescriptionItemRequest;
import com.phenikaa.cse702051.medbook.service.PrescriptionItemService;

/**
 * Dòng thuốc của đơn thuốc (YCCN-18). Đơn lấy từ đường dẫn; quyền và khóa theo trạng thái lần khám nằm ở
 * {@link PrescriptionItemService}. Không có tra cứu dòng thuốc theo tên: việc đó từng làm lộ đơn giữa các bệnh
 * nhân và giao diện không dùng. Mã dòng thuốc trên đường dẫn chỉ nhận chữ số nên đường dẫn tra cứu cũ trả 404.
 */
@RestController
@RequestMapping("/api/v1")
public class PrescriptionItemController {

    private final PrescriptionItemService prescriptionItemService;

    public PrescriptionItemController(PrescriptionItemService prescriptionItemService) {
        this.prescriptionItemService = prescriptionItemService;
    }

    @PostMapping("/prescriptions/{prescriptionId}/items")
    public PrescriptionItemDTO create(@PathVariable Long prescriptionId,
            @RequestBody PrescriptionItemRequest request) {
        return prescriptionItemService.create(prescriptionId, request);
    }

    @GetMapping("/prescriptions/{prescriptionId}/items")
    public List<PrescriptionItemDTO> listByPrescription(@PathVariable Long prescriptionId) {
        return prescriptionItemService.listByPrescription(prescriptionId);
    }

    @GetMapping("/prescription-items/{id:\\d+}")
    public PrescriptionItemDTO get(@PathVariable Long id) {
        return prescriptionItemService.get(id);
    }

    @PutMapping("/prescription-items/{id:\\d+}")
    public PrescriptionItemDTO update(@PathVariable Long id, @RequestBody PrescriptionItemRequest request) {
        return prescriptionItemService.update(id, request);
    }

    @DeleteMapping("/prescription-items/{id:\\d+}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        prescriptionItemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
