package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.repository.JpaMedicalRecordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EncounterService {

    private final EncounterRepository encounterRepository;
    private final AppointmentRepository appointmentRepository;
    private final JpaMedicalRecordRepository medicalRecordRepository;

    public Encounter createEncounter(
            Long medicalRecordId,
            Long appointmentId,
            Long doctorId,
            String chiefComplaint,
            String diagnosis,
            String clinicalNotes,
            String treatmentPlan,
            String followUpNote) {

        MedicalRecord medicalRecord = medicalRecordRepository.findById(medicalRecordId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ bệnh án"));

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy lịch khám"));

        Encounter encounter = Encounter.builder()
                .medicalRecord(medicalRecord)
                .appointment(appointment)
                .doctor(appointment.getDoctor())
                .encounterAt(LocalDateTime.now())
                .chiefComplaint(chiefComplaint)
                .diagnosis(diagnosis)
                .clinicalNotes(clinicalNotes)
                .treatmentPlan(treatmentPlan)
                .followUpNote(followUpNote)
                .status("COMPLETED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return encounterRepository.save(encounter);
    }
}
