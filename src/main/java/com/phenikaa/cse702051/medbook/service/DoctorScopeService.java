package com.phenikaa.cse702051.medbook.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalRecordRepository;

@Service
public class DoctorScopeService implements DoctorScopePolicy {

    private final AppointmentRepository appointmentRepository;
    private final EncounterRepository encounterRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    public DoctorScopeService(
            AppointmentRepository appointmentRepository,
            EncounterRepository encounterRepository,
            MedicalRecordRepository medicalRecordRepository) {
        this.appointmentRepository = appointmentRepository;
        this.encounterRepository = encounterRepository;
        this.medicalRecordRepository = medicalRecordRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isResponsible(Long doctorId, Long patientId) {
        if (doctorId == null || patientId == null) {
            return false;
        }
        if (appointmentRepository.existsByDoctorIdAndPatientIdAndStatusNot(
                doctorId, patientId, AppointmentStatus.CANCELLED)) {
            return true;
        }
        return medicalRecordRepository.findByPatientId(patientId)
                .map(record -> encounterRepository.existsByDoctorIdAndMedicalRecordId(doctorId, record.getId()))
                .orElse(false);
    }
}
