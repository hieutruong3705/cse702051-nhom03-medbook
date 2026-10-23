package com.phenikaa.cse702051.medbook.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Từ người dùng đăng nhập (JWT) suy ra hồ sơ bệnh nhân / bác sĩ tương ứng. Các module
 * dùng lớp này thay vì tự tra bằng username hay nhận {@code patientId}/{@code doctorId}
 * từ request.
 */
@Service
public class CurrentActorService {

    private final CurrentUserService currentUserService;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    public CurrentActorService(
            CurrentUserService currentUserService,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository) {
        this.currentUserService = currentUserService;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Long> findCurrentPatientId() {
        return currentUserService.findCurrentUser()
                .flatMap(user -> patientRepository.findByUserId(user.userId()))
                .map(Patient::getId);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findCurrentDoctorId() {
        return currentUserService.findCurrentUser()
                .flatMap(user -> doctorRepository.findByUserId(user.userId()))
                .map(Doctor::getId);
    }

    /** ID hồ sơ bệnh nhân của người đăng nhập; không có hồ sơ → 403. */
    @Transactional(readOnly = true)
    public Long requireCurrentPatientId() {
        currentUserService.requireCurrentUser();
        return findCurrentPatientId()
                .orElseThrow(() -> new ForbiddenException("Tài khoản hiện tại không có hồ sơ bệnh nhân!"));
    }

    /** ID hồ sơ bác sĩ của người đăng nhập; không có hồ sơ → 403. */
    @Transactional(readOnly = true)
    public Long requireCurrentDoctorId() {
        currentUserService.requireCurrentUser();
        return findCurrentDoctorId()
                .orElseThrow(() -> new ForbiddenException("Tài khoản hiện tại không có hồ sơ bác sĩ!"));
    }
}
