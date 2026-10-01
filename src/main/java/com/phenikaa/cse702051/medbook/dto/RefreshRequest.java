package com.phenikaa.cse702051.medbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RefreshRequest(
        @NotBlank @Pattern(regexp = "[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}") String refreshToken) {}
