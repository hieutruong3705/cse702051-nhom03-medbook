package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.repository.UserRoleRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public CustomUserDetailsService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found")
                );

        if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new LockedException("User account is locked");
        }

        if ("DISABLED".equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("User account is disabled");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new DisabledException("User account is not active");
        }

        List<String> roleCodes =
                userRoleRepository.findRoleCodesByUserId(user.getId());

        String[] authorities = roleCodes.stream()
                .map(role -> role.startsWith("ROLE_")
                        ? role
                        : "ROLE_" + role)
                .toArray(String[]::new);

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(false)
                .accountLocked(false)
                .build();
    }
}