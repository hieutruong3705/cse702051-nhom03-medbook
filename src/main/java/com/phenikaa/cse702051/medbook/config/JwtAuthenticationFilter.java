package com.phenikaa.cse702051.medbook.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.phenikaa.cse702051.medbook.service.SessionService;

import java.io.IOException;
import java.util.List;

/**
 * Đọc header {@code Authorization: Bearer <jwt>}. Token hợp lệ (đúng chữ ký, chưa hết hạn,
 * chưa bị thu hồi, tài khoản còn hoạt động, đúng phiên bản) → đặt
 * {@link com.phenikaa.cse702051.medbook.security.AuthenticatedUser} vào SecurityContext. Ngược
 * lại KHÔNG đặt gì, để entry point của Spring Security trả 401 chuẩn {@code ApiError} (endpoint
 * công khai vẫn truy cập được như khách).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Thuộc tính request chứa {@code JwtClaims} của token đã xác thực (dùng cho đăng xuất). */
    public static final String CLAIMS_ATTRIBUTE = "medbook.jwt.claims";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final SessionService sessionService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, SessionService sessionService) {
        this.jwtUtil = jwtUtil;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();

            jwtUtil.parseClaims(token).ifPresent(claims -> sessionService.validate(claims).ifPresent(user -> {
                List<SimpleGrantedAuthority> authorities = user.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute(CLAIMS_ATTRIBUTE, claims);
            }));
        }

        filterChain.doFilter(request, response);
    }
}
