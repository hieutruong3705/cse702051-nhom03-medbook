package com.phenikaa.cse702051.medbook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private List<String> roles;
    private Long patientId;
    private Long doctorId;
    private String message;
}
