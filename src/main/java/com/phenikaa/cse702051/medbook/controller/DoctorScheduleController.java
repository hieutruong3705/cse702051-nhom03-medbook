package com.phenikaa.cse702051.medbook.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.AddBreakRequest;
import com.phenikaa.cse702051.medbook.dto.AppointmentSlotDTO;
import com.phenikaa.cse702051.medbook.dto.CreateScheduleRequest;
import com.phenikaa.cse702051.medbook.dto.DoctorScheduleDTO;
import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.UpdateScheduleRequest;
import com.phenikaa.cse702051.medbook.service.DoctorScheduleService;

import jakarta.validation.Valid;

/**
 * Ca làm việc của bác sĩ (YCCN-14). Chỉ vai trò DOCTOR (xem {@code SecurityConfig}); bác sĩ luôn lấy từ JWT
 * nên không có tham số {@code doctorId} nào trong request và không thể thao tác trên ca của người khác.
 */
@RestController
@RequestMapping("/api/v1/doctor-schedules")
public class DoctorScheduleController {

    private final DoctorScheduleService scheduleService;

    public DoctorScheduleController(DoctorScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** Ca của tôi theo ngày tăng dần; mặc định từ hôm nay (truyền {@code from} để xem cả ca đã qua). */
    @GetMapping
    public PageResponse<DoctorScheduleDTO> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return scheduleService.listMine(from, to, page, size);
    }

    /** Slot (trống/đã đặt) của tôi trong một ngày. */
    @GetMapping("/slots")
    public List<AppointmentSlotDTO> slotsOfDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return scheduleService.listMySlots(date);
    }

    /** Tạo ca và sinh slot. Ca chồng giờ → 422. */
    @PostMapping
    public ResponseEntity<DoctorScheduleDTO> create(@Valid @RequestBody CreateScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(request));
    }

    @GetMapping("/{id}")
    public DoctorScheduleDTO get(@PathVariable Long id) {
        return scheduleService.getMine(id);
    }

    /** Sửa giờ ca. Đã có lịch đặt trong ca → 409. */
    @PutMapping("/{id}")
    public DoctorScheduleDTO update(@PathVariable Long id, @Valid @RequestBody UpdateScheduleRequest request) {
        return scheduleService.update(id, request);
    }

    /** Xóa ca. Đã có lịch đặt trong ca → 409. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/breaks")
    public ResponseEntity<DoctorScheduleDTO> addBreak(
            @PathVariable Long id, @Valid @RequestBody AddBreakRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.addBreak(id, request));
    }

    @DeleteMapping("/{id}/breaks/{breakId}")
    public DoctorScheduleDTO removeBreak(@PathVariable Long id, @PathVariable Long breakId) {
        return scheduleService.removeBreak(id, breakId);
    }
}
