package com.phenikaa.cse702051.medbook.service;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;

    // YCCN 08, 25: Lấy danh sách bác sĩ đang hoạt động
    public List<Doctor> getAllActiveDoctors() {
        return doctorRepository.findAll().stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .toList();
    }

    // Chi tiết bác sĩ theo ID
    public Doctor getDoctorById(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ bác sĩ"));
    }

    // YCCN 08: Tìm bác sĩ theo chuyên khoa
    public List<Doctor> getDoctorsBySpecialty(Long specialtyId) {
        return doctorRepository.findBySpecialtyIdAndIsActiveTrue(specialtyId);
    }

    // YCCN 20: Admin tạo hồ sơ bác sĩ
    @Transactional
    public Doctor createDoctorProfile(Doctor doctor) {
        doctor.setIsActive(true);
        return doctorRepository.save(doctor);
    }

    // YCCN 20: Admin cập nhật hồ sơ bác sĩ
    @Transactional
    public Doctor updateDoctorProfile(Long id, Doctor doctorDetails) {
        Doctor doctor = getDoctorById(id);
        doctor.setFullName(doctorDetails.getFullName());
        doctor.setPhone(doctorDetails.getPhone());
        doctor.setBio(doctorDetails.getBio());
        doctor.setSpecialty(doctorDetails.getSpecialty());
        return doctorRepository.save(doctor);
    }

    // Admin ngừng hoạt động hồ sơ bác sĩ
    @Transactional
    public void deactivateDoctor(Long id) {
        Doctor doctor = getDoctorById(id);
        doctor.setIsActive(false);
        doctorRepository.save(doctor);
    }

    // Phương thức nội bộ cho Dev 4: Kiểm tra trạng thái hoạt động của bác sĩ
    public boolean isDoctorActive(Long doctorId) {
        Optional<Doctor> doctorOpt = doctorRepository.findById(doctorId);
        return doctorOpt.isPresent() && Boolean.TRUE.equals(doctorOpt.get().getIsActive());
    }
}