package com.phenikaa.cse702051.medbook.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.encounter.CreateEncounterRequest;
import com.phenikaa.cse702051.medbook.dto.encounter.EncounterDTO;
import com.phenikaa.cse702051.medbook.dto.encounter.EncounterSummaryDTO;
import com.phenikaa.cse702051.medbook.dto.encounter.UpdateEncounterRequest;
import com.phenikaa.cse702051.medbook.service.EncounterService;

import jakarta.validation.Valid;

/**
 * Lần khám (BE-04). {@code doctorId} và {@code medicalRecordId} không bao giờ nhận từ client. Quyền theo
 * vai trò ở {@code SecurityConfig}; quyền theo bản ghi và audit ở {@link EncounterService}.
 */
@RestController
@RequestMapping("/api/v1/encounters")
public class EncounterController {

    private final EncounterService encounterService;

    public EncounterController(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    /** Bác sĩ phụ trách bắt đầu khám từ lịch BOOKED: lịch → IN_PROGRESS, tạo lần khám OPEN. */
    @PostMapping
    public ResponseEntity<EncounterDTO> create(@Valid @RequestBody CreateEncounterRequest request) {
        EncounterDTO created = encounterService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/encounters/" + created.id())).body(created);
    }

    /** Lịch sử khám của bệnh nhân đang đăng nhập, mới nhất trước. */
    @GetMapping("/me")
    public ResponseEntity<PageResponse<EncounterSummaryDTO>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(encounterService.getMyEncounters(page, size));
    }

    /** Tra cứu của bác sĩ theo {@code appointmentId} hoặc {@code medicalRecordId} (truyền ít nhất một). */
    @GetMapping
    public ResponseEntity<PageResponse<EncounterSummaryDTO>> search(
            @RequestParam(required = false) Long appointmentId,
            @RequestParam(required = false) Long medicalRecordId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(encounterService.search(appointmentId, medicalRecordId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EncounterDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(encounterService.get(id));
    }

    /** Cập nhật nội dung khám hoặc hoàn thành khám ({@code status = COMPLETED}). */
    @PutMapping("/{id}")
    public ResponseEntity<EncounterDTO> update(@PathVariable Long id,
            @Valid @RequestBody UpdateEncounterRequest request) {
        return ResponseEntity.ok(encounterService.update(id, request));
    }
}
