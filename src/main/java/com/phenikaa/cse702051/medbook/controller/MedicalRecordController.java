package com.phenikaa.cse702051.medbook.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.MedicalRecordDTO;
import com.phenikaa.cse702051.medbook.service.MedicalRecordService;

@RestController
@RequestMapping({ "/api/v1/medical-records", "/api/medical-records" })
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    public MedicalRecordController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    /**
     * Điểm cuối xem chi tiết hồ sơ bệnh án - Có phân quyền chống IDOR (Buổi 04).
     *
     * @param id         Mã định danh hồ sơ bệnh án
     * @param authHeader Token Authorization từ header
     * @param patientId  Header tùy chọn mô phỏng Bệnh nhân (X-Patient-Id)
     * @param role       Header tùy chọn mô phỏng Vai trò (X-User-Role)
     * @return Dữ liệu hồ sơ bệnh án MedicalRecordDTO nếu hợp lệ
     */
    @GetMapping("/{id}")
    public ResponseEntity<MedicalRecordDTO> getById(@PathVariable Long id) {
        MedicalRecordDTO dto = medicalRecordService.getRecordForUser(id);

        return ResponseEntity.ok(dto);
    }

    @org.springframework.web.bind.annotation.PostMapping
    public ResponseEntity<com.phenikaa.cse702051.medbook.model.MedicalRecord> createRecord(@org.springframework.web.bind.annotation.RequestBody com.phenikaa.cse702051.medbook.model.MedicalRecord record) {
        return ResponseEntity.ok(medicalRecordService.createRecord(record));
    }
}
