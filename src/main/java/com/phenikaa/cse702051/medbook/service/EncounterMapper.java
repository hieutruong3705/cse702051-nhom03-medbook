package com.phenikaa.cse702051.medbook.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.phenikaa.cse702051.medbook.dto.encounter.EncounterDTO;
import com.phenikaa.cse702051.medbook.dto.encounter.EncounterSummaryDTO;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalRecordRepository;

/**
 * Chuyển {@link Encounter} sang DTO, tra tên bệnh nhân/bác sĩ theo lô (mỗi loại một truy vấn) để tránh N+1.
 * Phải gọi trong giao dịch (truy cập quan hệ lazy {@code MedicalRecord.patient}).
 */
@Component
public class EncounterMapper {

    private final MedicalRecordRepository medicalRecordRepository;
    private final DoctorRepository doctorRepository;

    public EncounterMapper(MedicalRecordRepository medicalRecordRepository, DoctorRepository doctorRepository) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.doctorRepository = doctorRepository;
    }

    public EncounterDTO toDTO(Encounter encounter, boolean editable) {
        Lookup lookup = lookup(List.of(encounter));
        return new EncounterDTO(
                encounter.getId(),
                encounter.getAppointmentId(),
                encounter.getMedicalRecordId(),
                lookup.patientId(encounter),
                lookup.patientName(encounter),
                encounter.getDoctorId(),
                lookup.doctorName(encounter),
                encounter.getEncounterAt(),
                encounter.getChiefComplaint(),
                encounter.getDiagnosis(),
                encounter.getClinicalNotes(),
                encounter.getTreatmentPlan(),
                encounter.getFollowUpNote(),
                encounter.getStatus(),
                editable,
                encounter.getCreatedAt(),
                encounter.getUpdatedAt());
    }

    public List<EncounterSummaryDTO> toSummaries(Collection<Encounter> encounters) {
        Lookup lookup = lookup(encounters);
        return encounters.stream()
                .map(encounter -> new EncounterSummaryDTO(
                        encounter.getId(),
                        encounter.getAppointmentId(),
                        encounter.getMedicalRecordId(),
                        lookup.patientId(encounter),
                        lookup.patientName(encounter),
                        encounter.getDoctorId(),
                        lookup.doctorName(encounter),
                        encounter.getEncounterAt(),
                        encounter.getChiefComplaint(),
                        encounter.getDiagnosis(),
                        encounter.getStatus()))
                .toList();
    }

    private Lookup lookup(Collection<Encounter> encounters) {
        Set<Long> recordIds = encounters.stream().map(Encounter::getMedicalRecordId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> doctorIds = encounters.stream().map(Encounter::getDoctorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, MedicalRecord> records = new HashMap<>();
        medicalRecordRepository.findAllById(recordIds).forEach(record -> records.put(record.getId(), record));
        Map<Long, Doctor> doctors = new HashMap<>();
        doctorRepository.findAllById(doctorIds).forEach(doctor -> doctors.put(doctor.getId(), doctor));
        return new Lookup(records, doctors);
    }

    private record Lookup(Map<Long, MedicalRecord> records, Map<Long, Doctor> doctors) {

        Long patientId(Encounter encounter) {
            MedicalRecord record = records.get(encounter.getMedicalRecordId());
            return record == null ? null : record.getPatientId();
        }

        String patientName(Encounter encounter) {
            MedicalRecord record = records.get(encounter.getMedicalRecordId());
            return record == null || record.getPatient() == null ? null : record.getPatient().getFullName();
        }

        String doctorName(Encounter encounter) {
            Doctor doctor = doctors.get(encounter.getDoctorId());
            return doctor == null ? null : doctor.getFullName();
        }
    }
}
