package com.phenikaa.cse702051.medbook.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.phenikaa.cse702051.medbook.dto.AppointmentDTO;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.MedicalService;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.model.Specialty;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;

/**
 * Chuyển {@link Appointment} sang {@link AppointmentDTO}. Với danh sách, tra tên bệnh nhân/dịch vụ
 * theo lô (mỗi loại một truy vấn) để tránh N+1. Phải gọi trong giao dịch (truy cập quan hệ lazy).
 */
@Component
public class AppointmentMapper {

    private final PatientRepository patientRepository;
    private final MedicalServiceRepository serviceRepository;

    public AppointmentMapper(PatientRepository patientRepository, MedicalServiceRepository serviceRepository) {
        this.patientRepository = patientRepository;
        this.serviceRepository = serviceRepository;
    }

    public AppointmentDTO toDTO(Appointment appointment) {
        return toDTOs(List.of(appointment)).getFirst();
    }

    public List<AppointmentDTO> toDTOs(Collection<Appointment> appointments) {
        Set<Long> patientIds = appointments.stream().map(Appointment::getPatientId).collect(Collectors.toSet());
        Set<Long> serviceIds = appointments.stream().map(Appointment::getServiceId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Patient> patients = new HashMap<>();
        patientRepository.findAllById(patientIds).forEach(p -> patients.put(p.getId(), p));
        Map<Long, MedicalService> services = new HashMap<>();
        serviceRepository.findAllById(serviceIds).forEach(s -> services.put(s.getId(), s));

        return appointments.stream().map(a -> {
            AppointmentSlot slot = a.getSlot();
            Doctor doctor = a.getDoctor();
            Specialty specialty = doctor.getSpecialty();
            Patient patient = patients.get(a.getPatientId());
            MedicalService service = a.getServiceId() == null ? null : services.get(a.getServiceId());
            return new AppointmentDTO(
                    a.getId(),
                    a.getStatus(),
                    a.getNotes(),
                    a.getCancelReason(),
                    a.getPatientId(),
                    patient == null ? null : patient.getFullName(),
                    patient == null ? null : patient.getPatientCode(),
                    doctor.getId(),
                    doctor.getFullName(),
                    specialty == null ? null : specialty.getId(),
                    specialty == null ? null : specialty.getName(),
                    slot.getId(),
                    slot.getSlotDate(),
                    slot.getStartTime(),
                    slot.getEndTime(),
                    a.getServiceId(),
                    service == null ? null : service.getName(),
                    a.getCreatedAt(),
                    a.getCancelledAt());
        }).toList();
    }
}
