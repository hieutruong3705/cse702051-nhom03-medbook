package com.phenikaa.cse702051.medbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.MedicalRecordDTO;
import com.phenikaa.cse702051.medbook.service.MedicalRecordService;

/**
 * Bệnh án (YCCN-07, 19). Chỉ có hai endpoint đọc; việc tạo bệnh án diễn ra khi đăng ký
 * bệnh nhân và việc cập nhật nội dung diễn ra qua luồng khám (Dev 4) — không có API ghi
 * nhận entity thô từ client.
 */
@RestController
@RequestMapping("/api/v1/medical-records")
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    public MedicalRecordController(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    /** Bệnh án của bệnh nhân đang đăng nhập — ghi audit. */
    @GetMapping("/me")
    public MedicalRecordDTO getMine() {
        return medicalRecordService.getMyRecord();
    }

    /** Chi tiết bệnh án: chủ sở hữu hoặc bác sĩ phụ trách; Admin bị từ chối — ghi audit. */
    @GetMapping("/{id}")
    public MedicalRecordDTO getById(@PathVariable Long id) {
        return medicalRecordService.getRecordForUser(id);
    }
}
