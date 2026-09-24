package com.phenikaa.cse702051.medbook.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.dto.SystemStatusDTO;
import com.phenikaa.cse702051.medbook.repository.SystemStatusRepository;

@Service
public class SystemStatusService {

    private final SystemStatusRepository systemStatusRepository;

    public SystemStatusService(SystemStatusRepository systemStatusRepository) {
        this.systemStatusRepository = systemStatusRepository;
    }

    public SystemStatusDTO getStatus() {
        return new SystemStatusDTO(
                systemStatusRepository.getApplicationName(),
                systemStatusRepository.getStatus(),
                systemStatusRepository.getStartedAt(),
                Instant.now());
    }
}
