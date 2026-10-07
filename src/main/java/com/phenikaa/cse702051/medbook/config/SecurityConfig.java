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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
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

    /**
     * Giao diện là bản build tĩnh cùng nguồn (không tải script, font hay ảnh từ máy chủ khác) nên chỉ cho phép
     * {@code 'self'}. Vue gắn style nội tuyến qua thuộc tính {@code style} nên {@code style-src} cần
     * {@code 'unsafe-inline'}; script nội tuyến vẫn bị cấm. {@code blob:} để xem trước và tải tệp đính kèm.
     */
    static final String DEFAULT_CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; "
            + "connect-src 'self'; frame-src 'self' blob:; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";

    /** Ứng dụng không dùng camera, micro, định vị hay thanh toán của trình duyệt nên tắt hẳn. */
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=()";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiErrorWriter apiErrorWriter;
    private final List<String> allowedOrigins;
    private final boolean openApiEnabled;
    private final String contentSecurityPolicy;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiErrorWriter apiErrorWriter,
            @Value("${medbook.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:8080}") String allowedOrigins,
            @Value("${medbook.openapi.enabled:false}") boolean openApiEnabled,
            @Value("${medbook.security.content-security-policy:}") String contentSecurityPolicy) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiErrorWriter = apiErrorWriter;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        this.openApiEnabled = openApiEnabled;
        this.contentSecurityPolicy = contentSecurityPolicy == null || contentSecurityPolicy.isBlank()
                ? DEFAULT_CONTENT_SECURITY_POLICY
                : contentSecurityPolicy.trim();
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

                // Header bảo mật cho cả API lẫn giao diện tĩnh. HSTS chỉ được gửi trên kết nối HTTPS
                // (sau reverse proxy cần server.forward-headers-strategy để nhận biết HTTPS).
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)))

                .authorizeHttpRequests(auth -> {
                    // === PUBLIC: giao diện tĩnh ===
                    auth.requestMatchers("/", "/index.html", "/favicon.svg", "/favicon.ico", "/icons.svg",
                            "/robots.txt", "/sitemap.xml", "/assets/**", "/error").permitAll();

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
                            API + "/system/status", API + "/health").permitAll();

                    // === ADMIN ===
                    auth.requestMatchers(API + "/admin/**").hasRole("ADMIN");
                    auth.requestMatchers(API + "/system/**").hasRole("ADMIN");
                    // Các đường dẫn danh mục và bác sĩ chỉ ĐỌC là công khai (hoặc cho bác sĩ). Mọi phương thức ghi
                    // trên chính các đường dẫn đó bị chặn với người không phải Admin, để một endpoint ghi lỡ
                    // thêm vào sau này không tự động mở cho mọi người đã đăng nhập. Việc quản trị nằm ở /admin/**.
                    for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH,
                            HttpMethod.DELETE)) {
                        auth.requestMatchers(method,
                                API + "/doctors", API + "/doctors/**",
                                API + "/specialties", API + "/specialties/**",
                                API + "/medical-services", API + "/medical-services/**",
                                API + "/medicines", API + "/medicines/**").hasRole("ADMIN");
                    }

                    // === DOCTOR ===
                    auth.requestMatchers(API + "/doctor-schedules", API + "/doctor-schedules/**").hasRole("DOCTOR");
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

                    // === Thông báo trong ứng dụng: chỉ bệnh nhân và bác sĩ (Admin không có lịch khám) ===
                    auth.requestMatchers(API + "/notifications", API + "/notifications/**")
                            .hasAnyRole("PATIENT", "DOCTOR");

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
