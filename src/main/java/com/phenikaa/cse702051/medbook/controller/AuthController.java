package com.phenikaa.cse702051.medbook.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.service.AuthService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(
            AuthService authService,
            AuthenticationManager authenticationManager
    ) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest request
    ) {
        try {
            User user = authService.register(
                    request.username(),
                    request.password(),
                    request.fullName(),
                    request.email(),
                    request.phone()
            );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Registration successful",
                            "userId", user.getId(),
                            "username", user.getUsername()
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "error", e.getMessage()
                    ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.username(),
                                    request.password()
                            )
                    );

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();

            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            securityContextRepository.saveContext(
                    context,
                    httpRequest,
                    null
            );

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Login successful",
                            "username", authentication.getName(),
                            "authorities", authentication.getAuthorities()
                                    .stream()
                                    .map(Object::toString)
                                    .toList()
                    )
            );

        } catch (Exception e) {
            SecurityContextHolder.clearContext();

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Invalid username or password"
                    ));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest request
    ) {
        SecurityContextHolder.clearContext();

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(
                Map.of("message", "Logout successful")
        );
    }

    public record RegisterRequest(
            String username,
            String password,
            String fullName,
            String email,
            String phone
    ) {
    }

    public record LoginRequest(
            String username,
            String password
    ) {
    }
}