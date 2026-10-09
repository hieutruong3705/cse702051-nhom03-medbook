
package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    // =========================================================
    // 1. XAC THUC VA LAY DANH TINH
    // =========================================================

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Ban can dang nhap");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Khong tim thay tai khoan"));
    }

    private Patient requireCurrentPatient(
            Authentication authentication) {

        User user = requireCurrentUser(authentication);

        return patientRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Tai khoan khong co ho so benh nhan"));
    }

    private Doctor requireCurrentDoctor(
            Authentication authentication) {

        User user = requireCurrentUser(authentication);

        return doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Tai khoan khong co ho so bac si"));
    }

    private Appointment requireAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Khong tim thay lich hen"));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    // =========================================================
    // 2. DAT LICH KHAM
    // =========================================================

    @Transactional
    public Appointment bookAppointment(
            Long slotId,
            String notes,
            Authentication authentication) {

        Patient patient = requireCurrentPatient(authentication);

        AppointmentSlot slot = appointmentSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Khong tim thay khung gio kham"));

        if (!Boolean.TRUE.equals(slot.getIsAvailable())
                || "BOOKED".equalsIgnoreCase(slot.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Khung gio nay da duoc dat hoac khong kha dung");
        }

        // Khoa slot bang optimistic locking.
        slot.setIsAvailable(false);
        slot.setStatus("BOOKED");

        try {
            appointmentSlotRepository.saveAndFlush(slot);
        } catch (OptimisticLockingFailureException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Khung gio vua duoc nguoi khac dat",
                    ex);
        }

        Appointment appointment = Appointment.builder()
                .patientId(patient.getId())
                .doctor(slot.getDoctor())
                .slot(slot)
                .status(AppointmentStatus.PENDING)
                .notes(notes)
                .build();

        return appointmentRepository.saveAndFlush(appointment);
    }

    // =========================================================
    // 3. HUY LICH KHAM
    // =========================================================

    @Transactional
    public Appointment cancelAppointment(
            Long appointmentId,
            Authentication authentication) {

        Patient patient = requireCurrentPatient(authentication);
        Appointment appointment = requireAppointment(appointmentId);

        if (!patient.getId().equals(appointment.getPatientId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Ban khong co quyen huy lich hen nay");
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.COMPLETED
                || appointment.getStatus() == AppointmentStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trang thai lich hen khong cho phep huy");
        }

        AppointmentSlot slot = appointment.getSlot();

        if (slot != null) {
            slot.setIsAvailable(true);
            slot.setStatus("AVAILABLE");
            appointmentSlotRepository.save(slot);
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);

        return appointmentRepository.save(appointment);
    }

    // =========================================================
    // 4. DOI LICH KHAM
    // =========================================================

    @Transactional
    public Appointment rescheduleAppointment(
            Long appointmentId,
            Long newSlotId,
            Authentication authentication) {

        Patient patient = requireCurrentPatient(authentication);
        Appointment appointment = requireAppointment(appointmentId);

        if (!patient.getId().equals(appointment.getPatientId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Ban khong co quyen doi lich hen nay");
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.COMPLETED
                || appointment.getStatus() == AppointmentStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trang thai lich hen khong cho phep doi");
        }

        if (newSlotId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Slot moi khong duoc de trong");
        }

        AppointmentSlot oldSlot = appointment.getSlot();

        if (oldSlot == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Lich hen hien tai khong co khung gio");
        }

        if (oldSlot.getId().equals(newSlotId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Slot moi phai khac slot hien tai");
        }

        AppointmentSlot newSlot = appointmentSlotRepository.findById(newSlotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Khung gio moi khong ton tai"));

        if (!Boolean.TRUE.equals(newSlot.getIsAvailable())
                || "BOOKED".equalsIgnoreCase(newSlot.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Khung gio moi da co nguoi dat");
        }

        // Chiem slot moi truoc.
        newSlot.setIsAvailable(false);
        newSlot.setStatus("BOOKED");

        try {
            appointmentSlotRepository.saveAndFlush(newSlot);
        } catch (OptimisticLockingFailureException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Khung gio moi vua duoc nguoi khac dat",
                    ex);
        }

        // Cap nhat appointment sang slot moi.
        appointment.setSlot(newSlot);
        appointment.setDoctor(newSlot.getDoctor());

        // Giai phong slot cu trong cung transaction.
        oldSlot.setIsAvailable(true);
        oldSlot.setStatus("AVAILABLE");
        appointmentSlotRepository.save(oldSlot);

        return appointmentRepository.saveAndFlush(appointment);
    }

    // =========================================================
    // 5. BAC SI CAP NHAT TRANG THAI
    // =========================================================

    @Transactional
    public Appointment updateStatus(
            Long appointmentId,
            AppointmentStatus newStatus,
            Authentication authentication) {

        Doctor doctor = requireCurrentDoctor(authentication);
        Appointment appointment = requireAppointment(appointmentId);

        if (appointment.getDoctor() == null
                || !doctor.getId().equals(appointment.getDoctor().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bac si khong phu trach lich kham nay");
        }

        if (newStatus == null
                || newStatus == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trang thai moi khong hop le");
        }

        appointment.setStatus(newStatus);

        return appointmentRepository.save(appointment);
    }

    // =========================================================
    // 6. DANH SACH LICH CUA BENH NHAN
    // =========================================================

    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsByPatient(
            Long patientId,
            Authentication authentication) {

        Patient patient = requireCurrentPatient(authentication);

        if (!patient.getId().equals(patientId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Ban khong co quyen xem lich cua benh nhan khac");
        }

        return appointmentRepository.findByPatientId(patient.getId());
    }

    // =========================================================
    // 7. DANH SACH LICH CUA BAC SI
    // =========================================================

    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsByDoctor(Long doctorId) {
        return appointmentRepository.findByDoctorId(doctorId);
    }

    // =========================================================
    // 8. CHI TIET LICH HEN - CHONG IDOR/BOLA
    // =========================================================

    @Transactional(readOnly = true)
    public Appointment getAppointmentById(
            Long id,
            Authentication authentication) {

        User user = requireCurrentUser(authentication);
        Appointment appointment = requireAppointment(id);

        if (isAdmin(authentication)) {
            return appointment;
        }

        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        if (roles.contains("ROLE_PATIENT")) {
            Patient patient = patientRepository
                    .findByUser_Id(user.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Tai khoan khong co ho so benh nhan"));

            if (patient.getId().equals(appointment.getPatientId())) {
                return appointment;
            }
        }

        if (roles.contains("ROLE_DOCTOR")) {
            Doctor doctor = doctorRepository
                    .findByUserId(user.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Tai khoan khong co ho so bac si"));

            if (appointment.getDoctor() != null
                    && doctor.getId().equals(appointment.getDoctor().getId())) {
                return appointment;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Ban khong co quyen truy cap lich hen nay");
    }
}
