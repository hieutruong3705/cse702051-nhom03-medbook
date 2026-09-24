package com.phenikaa.cse702051.medbook.dto;

import java.time.Instant;

public record SystemStatusDTO(
        String application,
        String status,
        Instant startedAt,
        Instant checkedAt
) {
}
