package com.phenikaa.cse702051.medbook.controller;

import com.phenikaa.cse702051.medbook.model.PrescriptionItem;
import com.phenikaa.cse702051.medbook.service.PrescriptionItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PrescriptionItemController {

    private final PrescriptionItemService prescriptionItemService;

    public PrescriptionItemController(
            PrescriptionItemService prescriptionItemService
    ) {
        this.prescriptionItemService = prescriptionItemService;
    }

    // Thêm thuốc vào đơn thuốc
    // POST /api/prescriptions/{id}/items
    @PostMapping("/prescriptions/{id}/items")
    public ResponseEntity<PrescriptionItem> createItem(
            @PathVariable Long id,
            @RequestBody PrescriptionItem item
    ) {
        return ResponseEntity.ok(
                prescriptionItemService.create(id, item)
        );
    }

    // Xem danh sách thuốc trong đơn
    // GET /api/prescriptions/{id}/items
    @GetMapping("/prescriptions/{id}/items")
    public ResponseEntity<List<PrescriptionItem>> getItems(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                prescriptionItemService.getByPrescriptionId(id)
        );
    }

    // Xem chi tiết một thuốc trong đơn
    // GET /api/prescription-items/{id}
    @GetMapping("/prescription-items/{id}")
    public ResponseEntity<PrescriptionItem> getItem(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                prescriptionItemService.getById(id)
        );
    }

    // Sửa thuốc trong đơn
    // PUT /api/prescription-items/{id}
    @PutMapping("/prescription-items/{id}")
    public ResponseEntity<PrescriptionItem> updateItem(
            @PathVariable Long id,
            @RequestBody PrescriptionItem item
    ) {
        return ResponseEntity.ok(
                prescriptionItemService.update(id, item)
        );
    }

    // Xóa thuốc khỏi đơn
    // DELETE /api/prescription-items/{id}
    @DeleteMapping("/prescription-items/{id}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable Long id
    ) {
        prescriptionItemService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Tìm thuốc theo tên
    // GET /api/prescription-items/search?medicineName=...
    @GetMapping("/prescription-items/search")
    public ResponseEntity<List<PrescriptionItem>> searchByMedicineName(
            @RequestParam String medicineName
    ) {
        return ResponseEntity.ok(
                prescriptionItemService.searchByMedicineName(medicineName)
        );
    }
}