package com.phenikaa.cse702051.medbook.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.phenikaa.cse702051.medbook.config.JwtUtil;
import com.phenikaa.cse702051.medbook.model.Role;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.model.UserRole;
import com.phenikaa.cse702051.medbook.model.UserRoleId;
import com.phenikaa.cse702051.medbook.repository.RoleRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.repository.UserRoleRepository;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.service.MedicalRecordService;

/**
 * Dữ liệu dựng thêm trên nền seed ({@code data.sql}) cho test. Mọi phương thức idempotent vì
 * các test dùng chung một Spring context (và một DB H2) trong cùng JVM.
 *
 * <p>Seed: bác sĩ 1 (user 2) – Nội tổng quát, bác sĩ 2 (user 3) – Nhi; bệnh nhân 1 (user 4), 2 (user 5).
 * Slot do lớp này tạo nằm ở ngày xa trong tương lai để không đụng slot của seed hay của test đặt lịch.
 */
@Component
public class ApiTestData {

    public static final long DOCTOR1_ID = 1L;
    public static final long DOCTOR2_ID = 2L;
    public static final long PATIENT1_ID = 1L;
    public static final long PATIENT2_ID = 2L;

    private static final AtomicInteger SLOT_COUNTER = new AtomicInteger();

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final EncounterRepository encounterRepository;
    private final MedicalRecordService medicalRecordService;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public ApiTestData(
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            AppointmentSlotRepository slotRepository,
            EncounterRepository encounterRepository,
            MedicalRecordService medicalRecordService,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil) {
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.encounterRepository = encounterRepository;
        this.medicalRecordService = medicalRecordService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Tạo (hoặc lấy lại) tài khoản ACTIVE với mật khẩu đã biết và các role cho trước. Dùng để test
     * đăng nhập thật vì mật khẩu của tài khoản seed không được ghi trong repo.
     */
    public User user(String username, String password, String... roleCodes) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            User user = userRepository.save(User.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(password))
                    .fullName("Test " + username)
                    .email(username.toLowerCase() + "@example.com")
                    .phone("0900000000")
                    .status("ACTIVE")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            for (String code : roleCodes) {
                Role role = roleRepository.findByCode(code).orElseThrow();
                userRoleRepository.save(UserRole.builder()
                        .id(new UserRoleId(user.getId(), role.getId()))
                        .user(user)
                        .role(role)
                        .createdAt(now)
                        .build());
            }
            return user;
        });
    }

    /** Bác sĩ dựng riêng cho một test (có tài khoản, hồ sơ bác sĩ và token) để không dính dữ liệu chung. */
    public record IsolatedDoctor(Long userId, Long doctorId, String token) {
    }

    /**
     * Bác sĩ hoàn toàn mới (khóa {@code key}): các test phạm vi bác sĩ dùng bác sĩ này cùng với bệnh nhân
     * {@link #extraPatient(String)} riêng để kết quả không phụ thuộc lịch hẹn mà test khác đã tạo trên
     * các tài khoản seed (DB dùng chung giữa các test).
     */
    public IsolatedDoctor isolatedDoctor(String key) {
        User user = user("isodoc_" + key, "Iso#Pass123", "DOCTOR");
        Doctor doctor = doctorRepository.findByUserId(user.getId()).orElseGet(() -> doctorRepository.save(
                Doctor.builder()
                        .userId(user.getId())
                        .fullName("Bác sĩ thử nghiệm " + key)
                        .licenseNumber("ISO-" + key)
                        .isActive(true)
                        .build()));
        String token = jwtUtil.issueToken(user.getId(), user.getUsername(), List.of("DOCTOR"), user.getTokenVersion())
                .token();
        return new IsolatedDoctor(user.getId(), doctor.getId(), "Bearer " + token);
    }

    /** Bệnh nhân có tài khoản dựng riêng cho một test (tài khoản, hồ sơ bệnh nhân và token). */
    public record IsolatedPatient(Long userId, Long patientId, String token) {
    }

    /**
     * Bệnh nhân hoàn toàn mới (khóa {@code key}) có tài khoản đăng nhập được; dùng khi test cần "chủ sở hữu" khác
     * bệnh nhân seed để kiểm tra quyền theo bản ghi mà không dính dữ liệu chung.
     */
    public IsolatedPatient isolatedPatient(String key) {
        User user = user("isopat_" + key, "Iso#Pass123", "PATIENT");
        Patient patient = patientRepository.findByUserId(user.getId()).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            return patientRepository.save(Patient.builder()
                    .user(user)
                    .patientCode("ISOP-" + key)
                    .fullName("Bệnh nhân thử nghiệm " + key)
                    .dateOfBirth(LocalDate.of(1990, 1, 1))
                    .genderCode("MALE")
                    .phone("0900000002")
                    .email(user.getEmail())
                    .status("ACTIVE")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        });
        String token = jwtUtil.issueToken(user.getId(), user.getUsername(), List.of("PATIENT"), user.getTokenVersion())
                .token();
        return new IsolatedPatient(user.getId(), patient.getId(), "Bearer " + token);
    }

    /** Lịch hẹn MỚI (luôn tạo bản ghi và slot riêng) giữa bác sĩ và bệnh nhân ở trạng thái cho trước. */
    public Appointment newAppointment(long doctorId, long patientId, AppointmentStatus status) {
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow();
        int n = SLOT_COUNTER.incrementAndGet();
        AppointmentSlot slot = slotRepository.save(AppointmentSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.now().plusYears(1))
                .startTime(LocalTime.of(6, 0).plusMinutes(5L * n))
                .endTime(LocalTime.of(6, 5).plusMinutes(5L * n))
                .isAvailable(false)
                .status("BOOKED")
                .build());
        return appointmentRepository.save(Appointment.builder()
                .patientId(patientId)
                .doctor(doctor)
                .slot(slot)
                .status(status)
                .build());
    }

    private static final AtomicInteger FUTURE_DAY = new AtomicInteger();

    /** Slot trống mới tinh ở ngày khác nhau xa trong tương lai (không đụng slot của test khác). */
    public AppointmentSlot futureSlot(long doctorId) {
        int n = FUTURE_DAY.incrementAndGet();
        return slot(doctorId, LocalDate.now().plusDays(60L + n), LocalTime.of(9, 0));
    }

    /** Slot trống ở thời điểm cho trước (luôn tạo bản ghi mới). */
    public AppointmentSlot slot(long doctorId, LocalDate date, LocalTime start) {
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow();
        return slotRepository.save(AppointmentSlot.builder()
                .doctor(doctor)
                .slotDate(date)
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .isAvailable(true)
                .status("AVAILABLE")
                .build());
    }

    /** Bệnh án của bệnh nhân (tự tạo nếu chưa có). */
    public MedicalRecord record(long patientId) {
        return medicalRecordService.getOrCreateByPatientId(patientId);
    }

    /** Bệnh nhân không gắn tài khoản, dùng để dựng tình huống phạm vi bác sĩ. */
    public Patient extraPatient(String code) {
        return patientRepository.findByPatientCode(code).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            return patientRepository.save(Patient.builder()
                    .patientCode(code)
                    .fullName("Bệnh nhân " + code)
                    .dateOfBirth(LocalDate.of(1995, 5, 5))
                    .genderCode("FEMALE")
                    .phone("0900000001")
                    .status("ACTIVE")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        });
    }

    /** Đảm bảo bác sĩ có một lịch hẹn với bệnh nhân ở trạng thái cho trước (không tạo trùng). */
    public void appointment(long doctorId, long patientId, AppointmentStatus status) {
        boolean exists = appointmentRepository.findByPatientId(patientId).stream()
                .anyMatch(a -> a.getDoctorId() == doctorId && a.getStatus() == status);
        if (exists) {
            return;
        }
        newAppointment(doctorId, patientId, status);
    }

    /** Đảm bảo có một lần khám giữa bác sĩ và bệnh án (không cần lịch hẹn). */
    public void encounter(long doctorId, long medicalRecordId) {
        if (encounterRepository.existsByDoctorIdAndMedicalRecordId(doctorId, medicalRecordId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Encounter encounter = new Encounter();
        encounter.setMedicalRecordId(medicalRecordId);
        encounter.setDoctorId(doctorId);
        encounter.setEncounterAt(now);
        encounter.setStatus("OPEN");
        encounter.setCreatedAt(now);
        encounter.setUpdatedAt(now);
        encounterRepository.save(encounter);
    }

    /**
     * Lần khám OPEN mới, gắn với một lịch hẹn IN_PROGRESS mới giữa bác sĩ và bệnh nhân (dựng thẳng vào DB,
     * không qua API) để test tệp/đơn thuốc có sẵn "lần khám đang mở".
     */
    public Encounter openEncounter(long doctorId, long patientId) {
        Appointment appointment = newAppointment(doctorId, patientId, AppointmentStatus.IN_PROGRESS);
        LocalDateTime now = LocalDateTime.now();
        Encounter encounter = new Encounter();
        encounter.setMedicalRecordId(medicalRecordService.getOrCreateByPatientId(patientId).getId());
        encounter.setAppointmentId(appointment.getId());
        encounter.setDoctorId(doctorId);
        encounter.setEncounterAt(now);
        encounter.setStatus("OPEN");
        encounter.setCreatedAt(now);
        encounter.setUpdatedAt(now);
        return encounterRepository.save(encounter);
    }
}
