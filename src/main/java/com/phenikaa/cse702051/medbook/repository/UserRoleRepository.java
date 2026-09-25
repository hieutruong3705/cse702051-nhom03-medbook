package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.UserRole;
import com.phenikaa.cse702051.medbook.model.UserRoleId;

import jakarta.annotation.PostConstruct;

@Repository
public class UserRoleRepository {

    private final Map<String, UserRole> userRoles = new ConcurrentHashMap<>();

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    public void initSeedRoles() {
        LocalDateTime now = LocalDateTime.now();
        // user 1: ADMIN
        addRole(1L, "ADMIN", now);
        // user 2, 3: DOCTOR
        addRole(2L, "DOCTOR", now);
        addRole(3L, "DOCTOR", now);
        // user 4: RECEPTIONIST
        addRole(4L, "RECEPTIONIST", now);
        // user 5, 6, 7: PATIENT
        addRole(5L, "PATIENT", now);
        addRole(6L, "PATIENT", now);
        addRole(7L, "PATIENT", now);
    }

    private void addRole(Long userId, String roleCode, LocalDateTime now) {
        roleRepository.findByCode(roleCode).ifPresent(role -> {
            userRepository.findById(userId).ifPresent(user -> {
                UserRoleId urId = new UserRoleId(userId, role.getId());
                UserRole ur = UserRole.builder().id(urId).user(user).role(role).createdAt(now).build();
                userRoles.put(userId + "_" + role.getId(), ur);
            });
        });
    }

    public UserRole save(UserRole userRole) {
        String key = userRole.getUser().getId() + "_" + userRole.getRole().getId();
        userRoles.put(key, userRole);
        return userRole;
    }

    public List<UserRole> findByUserIdWithRole(Long userId) {
        if (userRoles.isEmpty()) {
            initSeedRoles();
        }
        return userRoles.values().stream()
                .filter(ur -> ur.getUser() != null && ur.getUser().getId().equals(userId))
                .collect(Collectors.toList());
    }
}
