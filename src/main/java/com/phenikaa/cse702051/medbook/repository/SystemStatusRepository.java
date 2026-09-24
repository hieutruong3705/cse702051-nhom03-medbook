package com.phenikaa.cse702051.medbook.repository;

import java.time.Instant;

import org.springframework.stereotype.Repository;

@Repository
public class SystemStatusRepository {

    private final Instant startedAt = Instant.now();

    public String getApplicationName() {
        return "medbook";
    }

    public String getStatus() {
        return "UP";
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
