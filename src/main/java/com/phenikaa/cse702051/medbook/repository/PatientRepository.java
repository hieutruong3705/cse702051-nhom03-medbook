package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.Patient;

@Repository
public class PatientRepository {

    private final Map<Long, Patient> patientsById = new ConcurrentHashMap<>();
    private final Map<Long, Long> patientIdByUserId = new ConcurrentHashMap<>();
    private final Map<String, Long> patientIdByCode = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(10);

    public PatientRepository() {
        initSeedPatients();
    }

    private void initSeedPatients() {
        LocalDateTime now = LocalDateTime.now();
        saveDirect(Patient.builder()
                .id(1L)
                .patientCode("MR0001")
                .fullName("Nguyễn Minh An")
                .dateOfBirth(LocalDate.of(1990, 5, 12))
                .genderCode("MALE")
                .phone("0900000005")
                .email("patient.an@example.test")
                .bloodType("O+")
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build(), 5L);

        saveDirect(Patient.builder()
                .id(2L)
                .patientCode("MR0002")
                .fullName("Trần Gia Bình")
                .dateOfBirth(LocalDate.of(1985, 10, 24))
                .genderCode("MALE")
                .phone("0900000006")
                .email("patient.binh@example.test")
                .bloodType("A+")
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build(), 6L);

        saveDirect(Patient.builder()
                .id(3L)
                .patientCode("MR0003")
                .fullName("Lê Ngọc Chi")
                .dateOfBirth(LocalDate.of(1998, 2, 18))
                .genderCode("FEMALE")
                .phone("0900000007")
                .email("patient.chi@example.test")
                .bloodType("B+")
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build(), 7L);
    }

    private void saveDirect(Patient patient, Long userId) {
        patientsById.put(patient.getId(), patient);
        if (userId != null) {
            patientIdByUserId.put(userId, patient.getId());
        }
        if (patient.getPatientCode() != null) {
            patientIdByCode.put(patient.getPatientCode().toUpperCase(), patient.getId());
        }
    }

    public Patient save(Patient patient) {
        if (patient.getId() == null) {
            patient.setId(idGenerator.incrementAndGet());
        }
        Long userId = patient.getUser() != null ? patient.getUser().getId() : null;
        saveDirect(patient, userId);
        return patient;
    }

    public Optional<Patient> findById(Long id) {
        return Optional.ofNullable(patientsById.get(id));
    }

    public Optional<Patient> findByUserId(Long userId) {
        if (userId == null) return Optional.empty();
        Long pId = patientIdByUserId.get(userId);
        return pId != null ? Optional.ofNullable(patientsById.get(pId)) : Optional.empty();
    }

    public Optional<Patient> findByPatientCode(String code) {
        if (code == null) return Optional.empty();
        Long pId = patientIdByCode.get(code.toUpperCase());
        return pId != null ? Optional.ofNullable(patientsById.get(pId)) : Optional.empty();
    }

    public boolean existsByPatientCode(String code) {
        return code != null && patientIdByCode.containsKey(code.toUpperCase());
    }
}
