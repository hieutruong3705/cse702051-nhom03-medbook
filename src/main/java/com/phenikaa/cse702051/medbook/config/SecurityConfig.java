package com.phenikaa.cse702051.medbook.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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

import com.phenikaa.cse702051.medbook.exception.ErrorCode;
import com.phenikaa.cse702051.medbook.security.ApiErrorWriter;

/**
 * Ma trận phân quyền route cho toàn bộ API {@code /api/v1}. Quyền theo bản ghi
 * (bệnh nhân chỉ xem của mình, bác sĩ phụ trách...) được kiểm tra thêm ở tầng
 * service; ở đây chỉ chặn theo vai trò để mọi route mới đều có rào bảo vệ mặc
 * định ({@code anyRequest().authenticated()}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String API = "/api/v1";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiErrorWriter apiErrorWriter;
    private final List<String> allowedOrigins;
    private final boolean openApiEnabled;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiErrorWriter apiErrorWriter,
            @Value("${medbook.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:8080}") String allowedOrigins,
            @Value("${medbook.openapi.enabled:false}") boolean openApiEnabled) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiErrorWriter = apiErrorWriter;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        this.openApiEnabled = openApiEnabled;
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

                .authorizeHttpRequests(auth -> {
                    // === PUBLIC: giao diện tĩnh ===
                    auth.requestMatchers("/", "/index.html", "/favicon.svg", "/favicon.ico", "/icons.svg",
                            "/assets/**", "/error").permitAll();

                    // === PUBLIC: OpenAPI (chỉ khi medbook.openapi.enabled=true, mặc định tắt) ===
                    if (openApiEnabled) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }

                    // === PUBLIC: xác thực ===
                    auth.requestMatchers(HttpMethod.POST,
                            API + "/auth/register",
                            API + "/auth/login",
                            API + "/auth/refresh",
                            API + "/auth/forgot-password",
                            API + "/auth/reset-password").permitAll();

                    // === PUBLIC: danh mục và hồ sơ bác sĩ (chỉ đọc) ===
                    auth.requestMatchers(HttpMethod.GET,
                            API + "/doctors", API + "/doctors/**",
                            API + "/specialties", API + "/specialties/**",
                            API + "/medical-services", API + "/medical-services/**",
                            API + "/system/status").permitAll();

                    // === ADMIN ===
                    auth.requestMatchers(API + "/admin/**").hasRole("ADMIN");
                    auth.requestMatchers(API + "/audit-logs", API + "/audit-logs/**").hasRole("ADMIN");
                    auth.requestMatchers(API + "/roles", API + "/roles/**").hasRole("ADMIN");
                    auth.requestMatchers(API + "/user-roles", API + "/user-roles/**").hasRole("ADMIN");
                    auth.requestMatchers(API + "/system/**").hasRole("ADMIN");
                    auth.requestMatchers(API + "/appointments/admin/**").hasRole("ADMIN");
                    // Ghi vào danh mục/hồ sơ bác sĩ (đường dẫn cũ) chỉ dành cho Admin
                    for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH,
                            HttpMethod.DELETE)) {
                        auth.requestMatchers(method,
                                API + "/doctors/**", API + "/specialties/**",
                                API + "/medical-services/**", API + "/medicines/**").hasRole("ADMIN");
                    }
                    // Đánh dấu thu tiền/hủy hóa đơn (đường dẫn cũ): không dành cho bệnh nhân/bác sĩ
                    auth.requestMatchers(HttpMethod.PUT, API + "/invoices/*/pay", API + "/invoices/*/void")
                            .hasRole("ADMIN");

                    // === DOCTOR ===
                    auth.requestMatchers(API + "/doctor-schedules", API + "/doctor-schedules/**").hasRole("DOCTOR");
                    auth.requestMatchers(API + "/schedule-breaks", API + "/schedule-breaks/**").hasRole("DOCTOR");
                    auth.requestMatchers(HttpMethod.PATCH, API + "/appointments/*/status").hasRole("DOCTOR");
                    auth.requestMatchers(HttpMethod.GET, API + "/medicines", API + "/medicines/**")
                            .hasAnyRole("DOCTOR", "ADMIN");
                    // Lần khám và tệp: bắt đầu/sửa/tải lên/xóa chỉ bác sĩ; tra cứu theo lịch/bệnh án chỉ bác sĩ
                    auth.requestMatchers(HttpMethod.POST, API + "/encounters", API + "/encounters/*/attachments")
                            .hasRole("DOCTOR");
                    auth.requestMatchers(HttpMethod.PUT, API + "/encounters/*").hasRole("DOCTOR");
                    auth.requestMatchers(HttpMethod.GET, API + "/encounters").hasRole("DOCTOR");
                    auth.requestMatchers(HttpMethod.DELETE, API + "/attachments/*").hasRole("DOCTOR");

                    // === PATIENT ===
                    auth.requestMatchers(HttpMethod.POST, API + "/appointments").hasRole("PATIENT");
                    auth.requestMatchers(HttpMethod.PATCH,
                            API + "/appointments/*/cancel",
                            API + "/appointments/*/reschedule").hasRole("PATIENT");
                    auth.requestMatchers(API + "/patients/me").hasRole("PATIENT");
                    auth.requestMatchers(HttpMethod.GET, API + "/encounters/me").hasRole("PATIENT");
                    auth.requestMatchers(API + "/patients", API + "/patients/**").hasAnyRole("DOCTOR", "ADMIN");

                    // === Đã đăng nhập; service kiểm tra thêm quyền theo bản ghi ===
                    auth.requestMatchers(API + "/auth/**").authenticated();
                    auth.requestMatchers(API + "/users/me").authenticated();
                    auth.requestMatchers(API + "/users", API + "/users/**").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.GET, API + "/appointments", API + "/appointments/**")
                            .authenticated();
                    auth.requestMatchers(
                            API + "/appointment-slots/**",
                            API + "/medical-records", API + "/medical-records/**",
                            API + "/encounters", API + "/encounters/**",
                            API + "/prescriptions", API + "/prescriptions/**",
                            API + "/prescription-items", API + "/prescription-items/**",
                            API + "/attachments", API + "/attachments/**",
                            API + "/invoices", API + "/invoices/**").authenticated();

                    // Mặc định: mọi route chưa khai báo đều phải đăng nhập
                    auth.anyRequest().authenticated();
                })

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> apiErrorWriter.write(
                                request, response, ErrorCode.UNAUTHORIZED,
                                "Bạn chưa đăng nhập hoặc token đã hết hạn!"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> apiErrorWriter.write(
                                request, response, ErrorCode.FORBIDDEN,
                                "Bạn không có quyền truy cập tài nguyên này!")))

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
