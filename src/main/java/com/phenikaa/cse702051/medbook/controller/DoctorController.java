package com.phenikaa.cse702051.medbook.controller;

import java.util.List;

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

import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.service.DoctorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    // YCCN 08, 25: Xem danh sách bác sĩ công khai
    @GetMapping
    public ResponseEntity<List<Doctor>> getDoctors(
            @RequestParam(required = false) Long specialtyId) {
        if (specialtyId != null) {
            return ResponseEntity.ok(doctorService.getDoctorsBySpecialty(specialtyId));
        }
        return ResponseEntity.ok(doctorService.getAllActiveDoctors());
    }

    // Chi tiết bác sĩ
    @GetMapping("/{id}")
    public ResponseEntity<Doctor> getDoctorById(@PathVariable Long id) {
        return ResponseEntity.ok(doctorService.getDoctorById(id));
    }

    // YCCN 20: Tạo hồ sơ bác sĩ (Admin)
    @PostMapping("/admin")
    public ResponseEntity<Doctor> createDoctor(@RequestBody Doctor doctor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(doctorService.createDoctorProfile(doctor));
    }

    // YCCN 20: Sửa hồ sơ bác sĩ (Admin)
    @PutMapping("/admin/{id}")
    public ResponseEntity<Doctor> updateDoctor(@PathVariable Long id, @RequestBody Doctor doctor) {
        return ResponseEntity.ok(doctorService.updateDoctorProfile(id, doctor));
    }

    // Admin ngừng hoạt động hồ sơ
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> deactivateDoctor(@PathVariable Long id) {
        doctorService.deactivateDoctor(id);
        return ResponseEntity.noContent().build();
    }
}