package com.phenikaa.cse702051.medbook.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.service.AppointmentSlotService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/appointment-slots")
@RequiredArgsConstructor
public class AppointmentSlotController {

    private final AppointmentSlotService slotService;

    @GetMapping("/available")
    public ResponseEntity<List<AppointmentSlot>> getAvailableSlots(
            @RequestParam Long doctorId,
            @RequestParam(required = false) String date) {

        LocalDate slotDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        List<AppointmentSlot> slots = slotService.getAvailableSlots(doctorId, slotDate);
        return ResponseEntity.ok(slots);
    }
}
