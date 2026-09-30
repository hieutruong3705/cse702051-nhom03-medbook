package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.dto.MedicalRecordDTO;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.exception.UnauthorizedException;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.MedicalRecordRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepository patientRepository;

    public MedicalRecordService(MedicalRecordRepository medicalRecordRepository,
            PatientRepository patientRepository) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.patientRepository = patientRepository;
    }

    // ========== Internal methods for other services ==========

    /**
     * Tìm MedicalRecord theo ID - dùng nội bộ bởi
     * EncounterService/Dev4AuthorizationService
     */
    public MedicalRecord findById(Long id) {
        return medicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hồ sơ bệnh án với ID: " + id));
    }

    /**
     * Tìm MedicalRecord theo patientId - dùng nội bộ bởi EncounterService
     */
    public MedicalRecord findByPatientId(Long patientId) {
        return medicalRecordRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bệnh án cho bệnh nhân ID: " + patientId));
    }

    // ========== API methods ==========

    /**
     * YCCN-07: Lấy chi tiết hồ sơ bệnh án với kiểm soát phân quyền (chống IDOR)
     * Dùng JWT context thay vì fake token
     */
    public MedicalRecordDTO getRecordForUser(Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("Yêu cầu chưa được xác thực!");
        }

        MedicalRecord record = findById(id);

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isDoctor = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DOCTOR".equals(a.getAuthority()));
        boolean isPatient = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PATIENT".equals(a.getAuthority()));

        // ADMIN không được xem nội dung bệnh án (bảo vệ dữ liệu y tế)
        if (isAdmin && !isDoctor) {
            throw new ForbiddenException("Quản trị viên không được phép xem nội dung chi tiết bệnh án!");
        }

        // PATIENT chỉ xem bệnh án của chính mình
        if (isPatient && !isDoctor) {
            String username = auth.getName();
            Patient patient = patientRepository.findByUserUsername(username)
                    .orElseThrow(() -> new ForbiddenException("Không tìm thấy hồ sơ bệnh nhân!"));

            if (!patient.getId().equals(record.getPatientId())) {
                throw new ForbiddenException("Bạn không có quyền truy cập hồ sơ bệnh án này!");
            }
        }

        // DOCTOR xem được bệnh án của bệnh nhân mình phụ trách (logic mở rộng có thể
        // thêm sau)

        return toDTO(record);
    }

    /**
     * Lấy bệnh án của bệnh nhân hiện tại (PATIENT)
     */
    public List<MedicalRecordDTO> getMyRecords() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        Patient patient = patientRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ bệnh nhân!"));

        return medicalRecordRepository.findByPatientId(patient.getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * DOCTOR tạo bệnh án cho bệnh nhân
     */
    @Transactional
    public MedicalRecord createRecord(MedicalRecord record) {
        LocalDateTime now = LocalDateTime.now();
        if (record.getCreatedAt() == null)
            record.setCreatedAt(now);
        record.setUpdatedAt(now);
        if (record.getStatus() == null)
            record.setStatus("ACTIVE");

        // Tạo mã bệnh án tự động
        if (record.getRecordCode() == null || record.getRecordCode().isBlank()) {
            long count = medicalRecordRepository.count();
            record.setRecordCode(String.format("MR%04d", count + 1));
        }

        return medicalRecordRepository.save(record);
    }

    /**
     * DOCTOR cập nhật bệnh án
     */
    @Transactional
    public MedicalRecord updateRecord(Long id, MedicalRecord input) {
        MedicalRecord existing = findById(id);

        if (input.getBloodType() != null)
            existing.setBloodType(input.getBloodType());
        if (input.getChronicConditions() != null)
            existing.setChronicConditions(input.getChronicConditions());
        if (input.getAllergyNotes() != null)
            existing.setAllergyNotes(input.getAllergyNotes());
        if (input.getMedicalHistory() != null)
            existing.setMedicalHistory(input.getMedicalHistory());
        if (input.getCurrentMedications() != null)
            existing.setCurrentMedications(input.getCurrentMedications());
        existing.setUpdatedAt(LocalDateTime.now());

        return medicalRecordRepository.save(existing);
    }

    // ========== Helpers ==========

    private MedicalRecordDTO toDTO(MedicalRecord record) {
        return new MedicalRecordDTO(
                record.getId(),
                record.getRecordCode(),
                record.getPatientId(),
                record.getPatient() != null ? record.getPatient().getFullName() : null,
                record.getBloodType(),
                record.getChronicConditions(),
                record.getAllergyNotes(),
                record.getMedicalHistory(),
                record.getCurrentMedications(),
                record.getStatus());
    }
}
