package com.phenikaa.cse702051.medbook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình Spring Security TẠM THỜI cho phép tất cả request.
 * Sẽ thay thế bằng cấu hình JWT + phân quyền đầy đủ sau khi có Entity.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Tắt CSRF (dùng JWT thay thế)
                .csrf(csrf -> csrf.disable())

                // Cho phép tất cả request trong giai đoạn đầu hoặc kiểm soát phân quyền
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll())

                // Tắt form login mặc định
                .formLogin(form -> form.disable())

                // Tắt HTTP Basic
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}