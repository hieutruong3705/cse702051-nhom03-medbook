package com.phenikaa.cse702051.medbook.dto;

import java.math.BigDecimal;

public record MedicalServiceDTO(Long id, String code, String name, String description,
        Integer durationMinutes, BigDecimal price) {}
