package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.Doctor;

@Repository
public class DoctorRepository {

    private final Map<Long, Doctor> doctorsById = new ConcurrentHashMap<>();
    private final Map<Long, Long> doctorIdByUserId = new ConcurrentHashMap<>();

    public DoctorRepository() {
        LocalDateTime now = LocalDateTime.now();
        saveDirect(Doctor.builder().id(1L).doctorCode("DOC0001").specialty("Noi khoa").yearsExperience(10).status("ACTIVE").createdAt(now).updatedAt(now).build(), 2L);
        saveDirect(Doctor.builder().id(2L).doctorCode("DOC0002").specialty("Nhi khoa").yearsExperience(8).status("ACTIVE").createdAt(now).updatedAt(now).build(), 3L);
    }

    private void saveDirect(Doctor doctor, Long userId) {
        doctorsById.put(doctor.getId(), doctor);
        if (userId != null) {
            doctorIdByUserId.put(userId, doctor.getId());
        }
    }

    public Optional<Doctor> findById(Long id) {
        return Optional.ofNullable(doctorsById.get(id));
    }

    public Optional<Doctor> findByUserId(Long userId) {
        if (userId == null) return Optional.empty();
        Long docId = doctorIdByUserId.get(userId);
        return docId != null ? Optional.ofNullable(doctorsById.get(docId)) : Optional.empty();
    }
}
