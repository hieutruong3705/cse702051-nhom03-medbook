package com.phenikaa.cse702051.medbook.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.AppointmentSlotDTO;
import com.phenikaa.cse702051.medbook.dto.slot.EarliestSlotDTO;
import com.phenikaa.cse702051.medbook.service.AppointmentSlotService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/appointment-slots")
@RequiredArgsConstructor
public class AppointmentSlotController {

    private final AppointmentSlotService slotService;

    /**
     * Slot trống của một bác sĩ trong ngày (mặc định hôm nay). Có {@code serviceId} thì chỉ còn slot đủ dài cho dịch
     * vụ đó; không có thì tương đương {@code GET /doctors/{id}/slots}.
     */
    @GetMapping("/available")
    public ResponseEntity<List<AppointmentSlotDTO>> getAvailableSlots(
            @RequestParam Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long serviceId) {
        return ResponseEntity.ok(slotService.getAvailableSlots(doctorId, date, serviceId));
    }

    /** Các khung giờ sớm nhất đặt được cho một dịch vụ, lọc được theo chuyên khoa hoặc bác sĩ (bệnh nhân, lễ tân). */
    @GetMapping("/earliest")
    public List<EarliestSlotDTO> earliest(
            @RequestParam Long serviceId,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) Integer limit) {
        return slotService.findEarliest(serviceId, specialtyId, doctorId, from, limit);
    }
}
