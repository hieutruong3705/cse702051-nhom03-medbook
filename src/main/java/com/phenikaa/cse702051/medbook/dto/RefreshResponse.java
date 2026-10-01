package com.phenikaa.cse702051.medbook.dto;

public record RefreshResponse(String token, String tokenType, String expiresAt,
        String refreshToken, String refreshExpiresAt) {}
