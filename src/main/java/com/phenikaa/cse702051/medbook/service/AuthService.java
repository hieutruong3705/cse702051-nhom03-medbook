package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.config.JwtUtil;
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

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * YCCN-01: Đăng ký tài khoản Bệnh nhân mới kèm mã hóa mật khẩu BCrypt (cost factor 12)
     */
    public RegisterResponse registerPatient(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim();

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Tên đăng nhập '" + username + "' đã được sử dụng!");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email '" + email + "' đã được đăng ký tài khoản khác!");
        }

        LocalDateTime now = LocalDateTime.now();
        String encodedPassword = passwordEncoder.encode(request.getPassword());

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

        String patientCode = String.format("BN%04d", user.getId());

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
     * YCCN-02: Đăng nhập hệ thống, đối chiếu mật khẩu BCrypt và cấp JWT Token thực
     */
    public LoginResponse login(LoginRequest request) {
        String input = request.getUsernameOrEmail().trim();

        User user = userRepository.findByUsername(input)
                .or(() -> userRepository.findByEmail(input))
                .orElseThrow(() -> new UnauthorizedException("Tên đăng nhập/email hoặc mật khẩu không chính xác!"));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ForbiddenException("Tài khoản '" + user.getUsername() + "' đã bị khóa hoặc ngừng hoạt động!");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Tên đăng nhập/email hoặc mật khẩu không chính xác!");
        }

        List<UserRole> userRoles = userRoleRepository.findByUserIdWithRole(user.getId());
        List<String> roleCodes = userRoles.stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toList());
        if (roleCodes.isEmpty()) {
            roleCodes = List.of("PATIENT");
        }

        Long patientId = patientRepository.findByUserId(user.getId())
                .map(Patient::getId)
                .orElse(null);

        Long doctorId = doctorRepository.findByUserId(user.getId())
                .map(Doctor::getId)
                .orElse(null);

        // Sinh JWT Token thực (có chữ ký HS256, hết hạn sau 24h)
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), roleCodes);

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
