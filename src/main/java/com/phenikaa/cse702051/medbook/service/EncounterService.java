package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EncounterService {

    private final EncounterRepository encounterRepository;
    private final MedicalRecordService medicalRecordService;
    private final AppointmentService appointmentService;
    private final Dev4AuthorizationService authorizationService;

    public EncounterService(
            EncounterRepository encounterRepository,
            MedicalRecordService medicalRecordService,
            AppointmentService appointmentService,
            Dev4AuthorizationService authorizationService
    ) {
        this.encounterRepository = encounterRepository;
        this.medicalRecordService = medicalRecordService;
        this.appointmentService = appointmentService;
        this.authorizationService = authorizationService;
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Transactional
    public Encounter create(Encounter encounter) {

        if (encounter == null) {
            throw new IllegalArgumentException("Encounter không được null");
        }

        if (encounter.getMedicalRecordId() == null) {
            throw new IllegalArgumentException(
                    "medicalRecordId không được để trống"
            );
        }

        if (encounter.getDoctorId() == null) {
            throw new IllegalArgumentException(
                    "doctorId không được để trống"
            );
        }

        MedicalRecord medicalRecord =
                medicalRecordService.findById(
                        encounter.getMedicalRecordId()
                );

        if (!isDoctor()) {
            throw new IllegalArgumentException(
                    "Chỉ bác sĩ mới được tạo encounter"
            );
        }

        Long currentDoctorId =
                authorizationService.getCurrentDoctorId();

        if (!currentDoctorId.equals(encounter.getDoctorId())) {
            throw new IllegalArgumentException(
                    "Bạn không có quyền tạo encounter cho bác sĩ khác"
            );
        }

        if (encounter.getAppointmentId() != null) {

            Appointment appointment =
                    appointmentService.findById(
                            encounter.getAppointmentId()
                    ).orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy appointment với ID: "
                                            + encounter.getAppointmentId()
                            )
                    );

            if (appointment.getDoctorId() == null
                    || !currentDoctorId.equals(
                            appointment.getDoctorId()
                    )) {
                throw new IllegalArgumentException(
                        "Appointment không thuộc bác sĩ hiện tại"
                );
            }

            if (encounterRepository.existsByAppointmentId(
                    encounter.getAppointmentId()
            )) {
                throw new IllegalArgumentException(
                        "Appointment này đã có encounter"
                );
            }
        }

        if (encounter.getEncounterAt() == null) {
            encounter.setEncounterAt(LocalDateTime.now());
        }

        if (encounter.getStatus() == null
                || encounter.getStatus().isBlank()) {
            encounter.setStatus("OPEN");
        }

        LocalDateTime now = LocalDateTime.now();

        if (encounter.getCreatedAt() == null) {
            encounter.setCreatedAt(now);
        }

        encounter.setUpdatedAt(now);

        return encounterRepository.save(encounter);
    }

    // ============================================================
    // GET BY ID - RAW
    // ============================================================

    @Transactional(readOnly = true)
    public Encounter getById(Long id) {
        return encounterRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy encounter với ID: " + id
                        )
                );
    }

    // ============================================================
    // GET BY ID - ACCESS CONTROL
    // ============================================================

    @Transactional(readOnly = true)
    public Encounter getAccessibleById(Long id) {

        Encounter encounter = getById(id);

        if (isPatient()) {
            authorizationService.assertPatientOwnsEncounter(
                    encounter
            );
            return encounter;
        }

        if (isDoctor()) {
            authorizationService.assertDoctorOwnsEncounter(
                    encounter
            );
            return encounter;
        }

        throw new IllegalArgumentException(
                "Bạn không có quyền truy cập encounter này"
        );
    }

    // ============================================================
    // GET BY MEDICAL RECORD
    // ============================================================

    @Transactional(readOnly = true)
    public List<Encounter> getByMedicalRecordId(
            Long medicalRecordId
    ) {

        if (isPatient()) {

            authorizationService.assertPatientOwnsMedicalRecord(
                    medicalRecordId
            );

            return encounterRepository.findByMedicalRecordId(
                    medicalRecordId
            );
        }

        if (isDoctor()) {

            Long currentDoctorId =
                    authorizationService.getCurrentDoctorId();

            return encounterRepository
                    .findByMedicalRecordId(medicalRecordId)
                    .stream()
                    .filter(encounter ->
                            currentDoctorId.equals(
                                    encounter.getDoctorId()
                            )
                    )
                    .collect(Collectors.toList());
        }

        throw new IllegalArgumentException(
                "Bạn không có quyền truy cập medical record này"
        );
    }

    // ============================================================
    // GET MY ENCOUNTERS
    // ============================================================

    @Transactional(readOnly = true)
    public List<Encounter> getMyEncounters() {

        if (!isPatient()) {
            throw new IllegalArgumentException(
                    "Chỉ bệnh nhân mới được sử dụng chức năng này"
            );
        }

        Long patientId =
                authorizationService.getCurrentPatientId();

        return getEncountersByPatientId(patientId);
    }

    // ============================================================
    // GET DOCTOR ENCOUNTERS
    // ============================================================

    @Transactional(readOnly = true)
    public List<Encounter> getMyDoctorEncounters() {

        if (!isDoctor()) {
            throw new IllegalArgumentException(
                    "Chỉ bác sĩ mới được sử dụng chức năng này"
            );
        }

        Long doctorId =
                authorizationService.getCurrentDoctorId();

        return encounterRepository.findByDoctorId(
                doctorId
        );
    }

    // ============================================================
    // GET BY DOCTOR ID
    // ============================================================

    @Transactional(readOnly = true)
    public List<Encounter> getByDoctorId(
            Long doctorId
    ) {

        if (!isDoctor()) {
            throw new IllegalArgumentException(
                    "Chỉ bác sĩ mới được sử dụng chức năng này"
            );
        }

        Long currentDoctorId =
                authorizationService.getCurrentDoctorId();

        if (!currentDoctorId.equals(doctorId)) {
            throw new IllegalArgumentException(
                    "Bạn không có quyền xem encounter của bác sĩ khác"
            );
        }

        return encounterRepository.findByDoctorId(
                doctorId
        );
    }

    // ============================================================
    // GET BY PATIENT ID
    // ============================================================

    @Transactional(readOnly = true)
    public List<Encounter> getEncountersByPatientId(
            Long patientId
    ) {

        if (!isPatient()) {
            throw new IllegalArgumentException(
                    "Chỉ bệnh nhân mới được sử dụng chức năng này"
            );
        }

        Long currentPatientId =
                authorizationService.getCurrentPatientId();

        if (!currentPatientId.equals(patientId)) {
            throw new IllegalArgumentException(
                    "Bạn không có quyền xem encounter của bệnh nhân khác"
            );
        }

        MedicalRecord medicalRecord =
                medicalRecordService.findByPatientId(
                        patientId
                );

        return encounterRepository.findByMedicalRecordId(
                medicalRecord.getId()
        );
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @Transactional
    public Encounter update(
            Long id,
            Encounter input
    ) {

        if (input == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu update không được null"
            );
        }

        Encounter existing = getById(id);

        authorizationService.assertDoctorOwnsEncounter(
                existing
        );

        if (input.getAppointmentId() != null
                && !input.getAppointmentId().equals(
                        existing.getAppointmentId()
                )) {

            Appointment appointment =
                    appointmentService.findById(
                            input.getAppointmentId()
                    ).orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Không tìm thấy appointment với ID: "
                                            + input.getAppointmentId()
                            )
                    );

            Long currentDoctorId =
                    authorizationService.getCurrentDoctorId();

            if (appointment.getDoctorId() == null
                    || !currentDoctorId.equals(
                            appointment.getDoctorId()
                    )) {
                throw new IllegalArgumentException(
                        "Appointment không thuộc bác sĩ hiện tại"
                );
            }

            if (encounterRepository.existsByAppointmentId(
                    input.getAppointmentId()
            )) {
                throw new IllegalArgumentException(
                        "Appointment này đã có encounter"
                );
            }

            existing.setAppointmentId(
                    input.getAppointmentId()
            );
        }

        if (input.getEncounterAt() != null) {
            existing.setEncounterAt(
                    input.getEncounterAt()
            );
        }

        if (input.getChiefComplaint() != null) {
            existing.setChiefComplaint(
                    input.getChiefComplaint()
            );
        }

        if (input.getDiagnosis() != null) {
            existing.setDiagnosis(
                    input.getDiagnosis()
            );
        }

        if (input.getClinicalNotes() != null) {
            existing.setClinicalNotes(
                    input.getClinicalNotes()
            );
        }

        if (input.getTreatmentPlan() != null) {
            existing.setTreatmentPlan(
                    input.getTreatmentPlan()
            );
        }

        if (input.getFollowUpNote() != null) {
            existing.setFollowUpNote(
                    input.getFollowUpNote()
            );
        }

        if (input.getStatus() != null
                && !input.getStatus().isBlank()) {
            existing.setStatus(
                    input.getStatus()
            );
        }

        existing.setUpdatedAt(
                LocalDateTime.now()
        );

        return encounterRepository.save(existing);
    }

    // ============================================================
    // SECURITY HELPERS
    // ============================================================

    private boolean isPatient() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return authentication != null
                && authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_PATIENT".equals(
                                authority.getAuthority()
                        )
                );
    }

    private boolean isDoctor() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return authentication != null
                && authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_DOCTOR".equals(
                                authority.getAuthority()
                        )
                );
    }
}