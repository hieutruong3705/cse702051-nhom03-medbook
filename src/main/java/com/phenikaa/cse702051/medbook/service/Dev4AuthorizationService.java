package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class Dev4AuthorizationService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final MedicalRecordService medicalRecordService;

    public Dev4AuthorizationService(
            UserRepository userRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            MedicalRecordService medicalRecordService
    ) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.medicalRecordService = medicalRecordService;
    }

    /**
     * Lấy User hiện tại từ tài khoản đang đăng nhập.
     */
    public User getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new IllegalArgumentException(
                    "Người dùng chưa đăng nhập"
            );
        }

        return userRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy user đang đăng nhập"
                        )
                );
    }

    /**
     * Lấy Patient ID của user hiện tại.
     */
    public Long getCurrentPatientId() {
        User user = getCurrentUser();

        Patient patient =
                patientRepository.findByUserId(user.getId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Tài khoản hiện tại không liên kết với bệnh nhân"
                                )
                        );

        return patient.getId();
    }

    /**
     * Lấy Doctor ID của user hiện tại.
     */
    public Long getCurrentDoctorId() {
        User user = getCurrentUser();

        Doctor doctor =
                doctorRepository.findByUserId(user.getId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Tài khoản hiện tại không liên kết với bác sĩ"
                                )
                        );

        return doctor.getId();
    }

    /**
     * Kiểm tra patient hiện tại có sở hữu encounter hay không.
     */
    public void assertPatientOwnsEncounter(
            Encounter encounter
    ) {
        if (encounter == null) {
            throw new IllegalArgumentException(
                    "Encounter không tồn tại"
            );
        }

        MedicalRecord medicalRecord =
                medicalRecordService.findById(
                        encounter.getMedicalRecordId()
                );

        Long currentPatientId =
                getCurrentPatientId();

        if (!currentPatientId.equals(
                medicalRecord.getPatientId()
        )) {
            throw new IllegalArgumentException(
                    "Bạn không có quyền truy cập encounter này"
            );
        }
    }

    /**
     * Kiểm tra doctor hiện tại có phụ trách encounter hay không.
     */
    public void assertDoctorOwnsEncounter(
            Encounter encounter
    ) {
        if (encounter == null) {
            throw new IllegalArgumentException(
                    "Encounter không tồn tại"
            );
        }

        Long currentDoctorId =
                getCurrentDoctorId();

        if (!currentDoctorId.equals(
                encounter.getDoctorId()
        )) {
            throw new IllegalArgumentException(
                    "Bạn không có quyền thao tác encounter này"
            );
        }
    }

    /**
     * Kiểm tra patient hiện tại có sở hữu medical record hay không.
     */
    public void assertPatientOwnsMedicalRecord(
            Long medicalRecordId
    ) {
        MedicalRecord medicalRecord =
                medicalRecordService.findById(
                        medicalRecordId
                );

        Long currentPatientId =
                getCurrentPatientId();

        if (!currentPatientId.equals(
                medicalRecord.getPatientId()
        )) {
            throw new IllegalArgumentException(
                    "Bạn không có quyền truy cập medical record này"
            );
        }
    }
}