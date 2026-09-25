package com.phenikaa.cse702051.medbook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private Long patientId;
    private String patientCode;
    private String message;
}
