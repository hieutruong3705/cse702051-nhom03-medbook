package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.dto.LoginRequest;
import com.phenikaa.cse702051.medbook.dto.LoginResponse;
import com.phenikaa.cse702051.medbook.dto.RegisterRequest;
import com.phenikaa.cse702051.medbook.dto.RegisterResponse;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.UnauthorizedException;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.model.Role;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.model.UserRole;
import com.phenikaa.cse702051.medbook.model.UserRoleId;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.RoleRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.repository.UserRoleRepository;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * YCCN-01: Đăng ký tài khoản Bệnh nhân mới kèm mã hóa mật khẩu BCrypt (cost factor 12)
     */
    public RegisterResponse registerPatient(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim();

        // 1. Kiểm tra trùng lặp username
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Tên đăng nhập '" + username + "' đã được sử dụng!");
        }

        // 2. Kiểm tra trùng lặp email
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email '" + email + "' đã được đăng ký tài khoản khác!");
        }

        LocalDateTime now = LocalDateTime.now();

        // 3. Băm mật khẩu bằng BCryptPasswordEncoder(12)
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 4. Lưu User vào bảng users
        User user = User.builder()
                .username(username)
                .passwordHash(encodedPassword)
                .fullName(request.getFullName().trim())
                .email(email)
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();
        user = userRepository.save(user);

        // 5. Gán vai trò mặc định PATIENT
        Role patientRole = roleRepository.findByCode("PATIENT")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .code("PATIENT")
                        .name("Bệnh nhân")
                        .description("Người bệnh đăng ký khám")
                        .createdAt(now)
                        .build()));

        UserRole userRole = UserRole.builder()
                .id(new UserRoleId(user.getId(), patientRole.getId()))
                .user(user)
                .role(patientRole)
                .createdAt(now)
                .build();
        userRoleRepository.save(userRole);

        // 6. Sinh mã bệnh nhân duy nhất (ví dụ: BN0005)
        String patientCode = String.format("BN%04d", user.getId());

        // 7. Tạo hồ sơ bệnh nhân trong bảng patients
        Patient patient = Patient.builder()
                .user(user)
                .patientCode(patientCode)
                .fullName(user.getFullName())
                .dateOfBirth(request.getDateOfBirth() != null ? request.getDateOfBirth() : LocalDate.of(2000, 1, 1))
                .genderCode(request.getGenderCode() != null && !request.getGenderCode().isBlank() ? request.getGenderCode().toUpperCase() : "OTHER")
                .phone(user.getPhone() != null ? user.getPhone() : "0000000000")
                .email(user.getEmail())
                .address(request.getAddress())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .bloodType(request.getBloodType())
                .allergies(request.getAllergies())
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();
        patient = patientRepository.save(patient);

        return RegisterResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role("PATIENT")
                .patientId(patient.getId())
                .patientCode(patient.getPatientCode())
                .message("Đăng ký tài khoản bệnh nhân thành công!")
                .build();
    }

    /**
     * YCCN-02: Đăng nhập hệ thống, đối chiếu mật khẩu BCrypt và cấp Token phiên làm việc
     */
    public LoginResponse login(LoginRequest request) {
        String input = request.getUsernameOrEmail().trim();

        // 1. Tìm người dùng theo username hoặc email
        User user = userRepository.findByUsername(input)
                .or(() -> userRepository.findByEmail(input))
                .orElseThrow(() -> new UnauthorizedException("Tên đăng nhập/email hoặc mật khẩu không chính xác!"));

        // 2. Kiểm tra trạng thái hoạt động
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ForbiddenException("Tài khoản '" + user.getUsername() + "' đã bị khóa hoặc ngừng hoạt động! Vui lòng liên hệ Admin.");
        }

        // 3. Đối chiếu mật khẩu bằng BCrypt
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Tên đăng nhập/email hoặc mật khẩu không chính xác!");
        }

        // 4. Lấy danh sách vai trò
        List<UserRole> userRoles = userRoleRepository.findByUserIdWithRole(user.getId());
        List<String> roleCodes = userRoles.stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toList());
        if (roleCodes.isEmpty()) {
            roleCodes = List.of("PATIENT");
        }

        // 5. Tìm patientId hoặc doctorId (nếu có)
        Long patientId = patientRepository.findByUserId(user.getId())
                .map(Patient::getId)
                .orElse(null);

        Long doctorId = doctorRepository.findByUserId(user.getId())
                .map(Doctor::getId)
                .orElse(null);

        // 6. Sinh Bearer Token
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                UUID.randomUUID().toString().replace("-", "") + "." +
                System.currentTimeMillis();

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roleCodes)
                .patientId(patientId)
                .doctorId(doctorId)
                .message("Đăng nhập thành công!")
                .build();
    }
}
