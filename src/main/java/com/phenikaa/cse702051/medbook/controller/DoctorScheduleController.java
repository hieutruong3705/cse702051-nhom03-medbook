package com.phenikaa.cse702051.medbook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping({"/api/v1/doctor/schedules", "/api/doctor-schedules"})
public class DoctorScheduleController {

    // 1. Tạo ca làm việc mới
    @PostMapping
    public ResponseEntity<?> createSchedule(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "message", "Tao ca lam viec thanh cong",
            "scheduleId", 1
        ));
    }

    // 2. Bác sĩ xem ca làm việc của mình
    @GetMapping("/my-shifts")
    public ResponseEntity<?> getMyShifts(@RequestHeader(value = "X-User-Id", defaultValue = "2") Long doctorId) {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", List.of(Map.of(
                "scheduleId", 1,
                "doctorId", doctorId,
                "workDate", "2026-10-05",
                "startTime", "08:00",
                "endTime", "12:00"
            ))
        ));
    }

    // 3. Thuật toán sinh lưới giờ trống trừ giờ nghỉ (Cốt lõi Buổi 6 - Luồng 3)
    @GetMapping("/{id}/available-slots")
    public ResponseEntity<?> getAvailableSlots(
            @PathVariable Long id,
            @RequestParam(defaultValue = "30") int durationMinutes) {

        // Giả lập ca: 08:00 - 12:00, giờ nghỉ: 10:00 - 10:45
        LocalTime shiftStart = LocalTime.of(8, 0);
        LocalTime shiftEnd = LocalTime.of(12, 0);
        LocalTime breakStart = LocalTime.of(10, 0);
        LocalTime breakEnd = LocalTime.of(10, 45);

        List<Map<String, String>> slots = new ArrayList<>();
        LocalTime current = shiftStart;

        while (!current.plusMinutes(durationMinutes).isAfter(shiftEnd)) {
            LocalTime candidateEnd = current.plusMinutes(durationMinutes);

            // Kiểm tra va chạm giờ nghỉ: (start < breakEnd) && (end > breakStart)
            if (current.isBefore(breakEnd) && candidateEnd.isAfter(breakStart)) {
                // Nhảy cóc qua khỏi mốc kết thúc giờ nghỉ
                current = breakEnd;
                continue;
            }

            slots.add(Map.of(
                "startTime", current.toString(),
                "endTime", candidateEnd.toString(),
                "status", "AVAILABLE"
            ));
            current = candidateEnd;
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "scheduleId", id,
            "totalSlots", slots.size(),
            "availableSlots", slots
        ));
    }

    // 4. Thêm khoảng nghỉ đột xuất
    @PostMapping("/{id}/breaks")
    public ResponseEntity<?> addBreak(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "message", "Them khoang nghi thanh cong",
            "scheduleId", id
        ));
    }

    // 5. Cập nhật ca làm việc
    @PutMapping("/{id}")
    public ResponseEntity<?> updateSchedule(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Cap nhat ca lam viec thanh cong",
            "scheduleId", id
        ));
    }
}