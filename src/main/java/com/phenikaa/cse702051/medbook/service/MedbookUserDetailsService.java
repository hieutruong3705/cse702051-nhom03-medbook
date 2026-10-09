
package com.phenikaa.cse702051.medbook.service;

import java.util.List;
import java.util.Locale;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.repository.UserRoleRepository;

@Service
public class MedbookUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public MedbookUserDetailsService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid username or password"));

        boolean active = user.getStatus() != null
                && "ACTIVE".equalsIgnoreCase(user.getStatus().trim());

        if (!active) {
            throw new UsernameNotFoundException("Invalid username or password");
        }

        List<SimpleGrantedAuthority> authorities =
                userRoleRepository.findByUser_Id(user.getId()).stream()
                        .map(userRole -> userRole.getRole().getCode())
                        .filter(code -> code != null && !code.isBlank())
                        .map(code -> "ROLE_" + code.trim()
                                .toUpperCase(Locale.ROOT))
                        .distinct()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

        if (authorities.isEmpty()) {
            throw new UsernameNotFoundException("Invalid username or password");
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .build();
    }
}
