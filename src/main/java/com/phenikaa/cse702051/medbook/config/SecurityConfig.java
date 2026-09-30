package com.phenikaa.cse702051.medbook.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // === PUBLIC ===
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**", "/css/**", "/js/**")
                        .permitAll()
                        .requestMatchers("/error").permitAll()

                        // Danh mục công khai (GET)
                        .requestMatchers(HttpMethod.GET, "/api/doctors/**", "/api/v1/doctors/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/specialties/**", "/api/v1/specialties/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/medical-services/**", "/api/v1/services/**").permitAll()

                        // === ADMIN ONLY ===
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/roles/**").hasRole("ADMIN")
                        .requestMatchers("/api/user-roles/**").hasRole("ADMIN")
                        .requestMatchers("/api/system/**").hasRole("ADMIN")
                        .requestMatchers("/api/data-metrics/**").hasRole("ADMIN")

                        // Admin danh mục CUD
                        .requestMatchers(HttpMethod.POST, "/api/doctors/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/doctors/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/doctors/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/specialties/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/specialties/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/specialties/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/medical-services/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/medical-services/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/medical-services/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/medicines/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/medicines/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/medicines/**").hasRole("ADMIN")

                        // === DOCTOR ===
                        .requestMatchers("/api/encounters/**").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/api/prescriptions/**").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/api/prescription-items/**").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/api/doctor-schedules/**").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/api/schedule-breaks/**").hasAnyRole("DOCTOR", "ADMIN")

                        // === PATIENT ===
                        .requestMatchers("/api/appointments/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                        .requestMatchers("/api/patients/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")

                        // === Authenticated ===
                        .requestMatchers(HttpMethod.GET, "/api/medicines/**").authenticated()
                        .requestMatchers("/api/medical-records/**", "/api/v1/medical-records/**").authenticated()
                        .requestMatchers("/api/appointment-slots/**").authenticated()
                        .requestMatchers("/api/attachments/**").authenticated()
                        .requestMatchers("/api/invoices/**").authenticated()
                        .requestMatchers("/api/invoice-reports/**").authenticated()

                        .anyRequest().authenticated())

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            new ObjectMapper().writeValue(response.getOutputStream(),
                                    Map.of("status", 401, "error", "Unauthorized",
                                            "message", "Bạn chưa đăng nhập hoặc token đã hết hạn!"));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            new ObjectMapper().writeValue(response.getOutputStream(),
                                    Map.of("status", 403, "error", "Forbidden",
                                            "message", "Bạn không có quyền truy cập tài nguyên này!"));
                        }))

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000", "http://localhost:8080"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}