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
import com.phenikaa.cse702051.medbook.service.AppointmentSlotService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/appointment-slots")
@RequiredArgsConstructor
public class AppointmentSlotController {

    private final AppointmentSlotService slotService;

    /** Slot trống của một bác sĩ trong ngày (mặc định hôm nay). Tương đương {@code GET /doctors/{id}/slots}. */
    @GetMapping("/available")
    public ResponseEntity<List<AppointmentSlotDTO>> getAvailableSlots(
            @RequestParam Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(slotService.getAvailableSlots(doctorId, date));
    }
}
