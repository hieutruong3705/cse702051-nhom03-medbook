package com.phenikaa.cse702051.medbook.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
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

@Service
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    public MedicalRecordService(
            MedicalRecordRepository medicalRecordRepository,
            AppointmentRepository appointmentRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            UserRepository userRepository) {

        this.medicalRecordRepository = medicalRecordRepository;
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
    }

    /**
     * Lấy hồ sơ bệnh án sau khi xác thực danh tính
     * và kiểm tra quyền truy cập đối tượng.
     */
    public MedicalRecordDTO getRecordForUser(
            Long id,
            Authentication authentication) {

        // 1. Kiểm tra xác thực.
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Ban can dang nhap");
        }

        // 2. Lấy tài khoản từ danh tính đã được Spring Security xác thực.
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Khong tim thay tai khoan"));

        // 3. Tìm hồ sơ bệnh án.
        MedicalRecordDTO record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Khong tim thay ho so benh an"));

        // 4. Lấy các vai trò từ Spring Security.
        Set<String> roles = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // 5. Quản trị viên không mặc nhiên được xem bệnh án.
        if (roles.contains("ROLE_ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tai khoan quan tri khong duoc phep xem chi tiet benh an");
        }

        // 6. Bệnh nhân chỉ được xem hồ sơ của chính mình.
        if (roles.contains("ROLE_PATIENT")) {

            Patient patient = patientRepository
                    .findByUser_Id(user.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Tai khoan khong co ho so benh nhan"));

            if (!patient.getId().equals(record.patientId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Ban khong co quyen xem ho so benh an nay");
            }

            return record;
        }

        // 7. Bác sĩ chỉ được xem hồ sơ bệnh nhân có lịch hẹn
        // với bác sĩ đó; lịch hẹn đã hủy không được tính.
        if (roles.contains("ROLE_DOCTOR")) {

            Doctor doctor = doctorRepository
                    .findByUserId(user.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Tai khoan khong co ho so bac si"));

            boolean hasRelatedAppointment =
                    appointmentRepository
                            .findByDoctorId(doctor.getId())
                            .stream()
                            .filter(appointment ->
                                    appointment.getStatus() != null
                                    && appointment.getStatus()
                                            != AppointmentStatus.CANCELLED)
                            .map(Appointment::getPatientId)
                            .anyMatch(record.patientId()::equals);

            if (!hasRelatedAppointment) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Bac si khong co lich hen hop le lien quan den benh nhan nay");
            }

            return record;
        }

        // 8. Từ chối các vai trò khác.
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Vai tro khong duoc phep xem ho so benh an");
    }
}
