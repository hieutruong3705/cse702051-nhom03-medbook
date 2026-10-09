package com.phenikaa.cse702051.medbook;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.phenikaa.cse702051.medbook.dto.MedicalRecordDTO;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalRecordRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.service.MedicalRecordService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceTest {

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MedicalRecordService medicalRecordService;

    private User user;
    private Patient patient;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(10L);
        user.setUsername("patient.test");

        patient = new Patient();
        patient.setId(1L);
    }

    private UsernamePasswordAuthenticationToken authentication(
            String username, String role) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private MedicalRecordDTO record(Long id, Long patientId) {
        return new MedicalRecordDTO(
                id,
                "MR" + id,
                patientId,
                "Test Patient",
                "O+",
                "None",
                "None",
                "None",
                "None",
                "ACTIVE");
    }

    @Test
    void unauthenticatedUser_returns401() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> medicalRecordService.getRecordForUser(1L, null));

        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void patientCanReadOwnRecord() {
        when(userRepository.findByUsername("patient.test"))
                .thenReturn(Optional.of(user));
        when(patientRepository.findByUser_Id(10L))
                .thenReturn(Optional.of(patient));
        when(medicalRecordRepository.findById(1L))
                .thenReturn(Optional.of(record(1L, 1L)));

        MedicalRecordDTO result = medicalRecordService.getRecordForUser(
                1L, authentication("patient.test", "PATIENT"));

        assertEquals(1L, result.patientId());
    }

    @Test
    void patientCannotReadAnotherPatientsRecord() {
        when(userRepository.findByUsername("patient.test"))
                .thenReturn(Optional.of(user));
        when(patientRepository.findByUser_Id(10L))
                .thenReturn(Optional.of(patient));
        when(medicalRecordRepository.findById(2L))
                .thenReturn(Optional.of(record(2L, 2L)));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> medicalRecordService.getRecordForUser(
                        2L, authentication("patient.test", "PATIENT")));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void adminCannotReadMedicalRecord() {
        User admin = new User();
        admin.setId(20L);
        admin.setUsername("admin.test");

        when(userRepository.findByUsername("admin.test"))
                .thenReturn(Optional.of(admin));
        when(medicalRecordRepository.findById(1L))
                .thenReturn(Optional.of(record(1L, 1L)));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> medicalRecordService.getRecordForUser(
                        1L, authentication("admin.test", "ADMIN")));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void doctorCannotReadRecordWithoutRelatedAppointment() {
        User doctorUser = new User();
        doctorUser.setId(30L);
        doctorUser.setUsername("doctor.test");

        Doctor doctor = new Doctor();
        doctor.setId(7L);

        when(userRepository.findByUsername("doctor.test"))
                .thenReturn(Optional.of(doctorUser));
        when(medicalRecordRepository.findById(1L))
                .thenReturn(Optional.of(record(1L, 1L)));
        when(doctorRepository.findByUserId(30L))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findByDoctorId(7L))
                .thenReturn(List.of());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> medicalRecordService.getRecordForUser(
                        1L, authentication("doctor.test", "DOCTOR")));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void doctorCannotReadRecordBasedOnlyOnCancelledAppointment() {
        User doctorUser = new User();
        doctorUser.setId(30L);
        doctorUser.setUsername("doctor.test");

        Doctor doctor = new Doctor();
        doctor.setId(7L);

        Appointment appointment = new Appointment();
        appointment.setPatientId(1L);
        appointment.setStatus(AppointmentStatus.CANCELLED);

        when(userRepository.findByUsername("doctor.test"))
                .thenReturn(Optional.of(doctorUser));
        when(medicalRecordRepository.findById(1L))
                .thenReturn(Optional.of(record(1L, 1L)));
        when(doctorRepository.findByUserId(30L))
                .thenReturn(Optional.of(doctor));
        when(appointmentRepository.findByDoctorId(7L))
                .thenReturn(List.of(appointment));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> medicalRecordService.getRecordForUser(
                        1L, authentication("doctor.test", "DOCTOR")));

        assertEquals(403, ex.getStatusCode().value());
    }
}